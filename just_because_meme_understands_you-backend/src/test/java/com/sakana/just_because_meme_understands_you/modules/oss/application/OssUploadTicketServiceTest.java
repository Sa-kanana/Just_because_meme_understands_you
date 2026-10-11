package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.sakana.just_because_meme_understands_you.api.user.vo.OssUploadPolicyVO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 中文注释：验证服务端上传票据校验、精确对象键和 OSS 上传确认行为。 */
class OssUploadTicketServiceTest {

    private static final String DTO_CLASS =
            "com.sakana.just_because_meme_understands_you.api.user.dto.OssUploadTicketRequestDTO";
    private static final String CONFIRMATION_SERVICE_CLASS =
            "com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetUploadConfirmationService";

    @Test
    void issuesServerGeneratedExactKeyAndRegistersTemporaryAsset() throws Throwable {
        FileAssetMapper mapper = assetMapper();
        OssUploadPolicyService service = policyService(mapper);
        Object request = uploadRequest("meme", "test-image.png", "image/png", 1024L);

        OssUploadPolicyVO ticket = (OssUploadPolicyVO) invokeTicket(service, request, authenticatedRequest(42L, List.of("USER")));
        String objectKey = (String) ReflectionTestUtils.getField(ticket, "objectKey");
        String policyJson = new String(Base64.getDecoder().decode(ticket.getPolicy()), StandardCharsets.UTF_8);

        assertNotNull(ReflectionTestUtils.getField(ticket, "assetId"), "票据必须返回资产ID");
        assertTrue(objectKey.matches("tmp/meme/42/\\d{8}/[0-9a-f-]{36}\\.png"), "objectKey 必须由服务端生成");
        assertTrue(policyJson.contains("[\"eq\",\"$key\",\"" + objectKey + "\"]"),
                "PostObject policy 必须绑定唯一 objectKey");
        assertTrue(policyJson.contains("[\"eq\",\"$Content-Type\",\"image/png\"]"),
                "PostObject policy 必须绑定声明的 MIME");
        assertTrue(policyJson.contains("[\"content-length-range\",1,10485760]"),
                "PostObject policy 必须限制上传大小");
        verify(mapper).insert(any(FileAsset.class));
    }

    @Test
    void rejectsUnsupportedSceneAndDoesNotRegisterAsset() throws Throwable {
        FileAssetMapper mapper = assetMapper();
        OssUploadPolicyService service = policyService(mapper);

        assertThrows(BizException.class, () -> invokeTicket(service,
                uploadRequest("unknown", "test.png", "image/png", 10L),
                authenticatedRequest(42L, List.of("USER"))));
        verify(mapper, never()).insert(any(FileAsset.class));
    }

    @Test
    void rejectsMismatchBetweenImageExtensionAndMime() throws Throwable {
        FileAssetMapper mapper = assetMapper();
        OssUploadPolicyService service = policyService(mapper);

        assertThrows(BizException.class, () -> invokeTicket(service,
                uploadRequest("meme", "test.png", "image/jpeg", 10L),
                authenticatedRequest(42L, List.of("USER"))));
        verify(mapper, never()).insert(any(FileAsset.class));
    }

    @Test
    void rejectsFilesLargerThanTenMebibytes() throws Throwable {
        FileAssetMapper mapper = assetMapper();
        OssUploadPolicyService service = policyService(mapper);

        assertThrows(BizException.class, () -> invokeTicket(service,
                uploadRequest("meme", "test.png", "image/png", 10L * 1024 * 1024 + 1),
                authenticatedRequest(42L, List.of("USER"))));
        verify(mapper, never()).insert(any(FileAsset.class));
    }

    @Test
    void onlyAdministratorsCanRequestHomepageImageTickets() throws Throwable {
        FileAssetMapper mapper = assetMapper();
        OssUploadPolicyService service = policyService(mapper);

        assertThrows(BizException.class, () -> invokeTicket(service,
                uploadRequest("home", "banner.png", "image/png", 128L),
                authenticatedRequest(42L, List.of("USER"))));
        verify(mapper, never()).insert(any(FileAsset.class));

        Object request = uploadRequest("home", "banner.png", "image/png", 128L);
        OssUploadPolicyVO ticket = (OssUploadPolicyVO) invokeTicket(
                service, request, authenticatedRequest(42L, List.of("ADMIN")));
        assertTrue(((String) ReflectionTestUtils.getField(ticket, "objectKey")).startsWith("tmp/home/42/"));
    }

    @Test
    void confirmsOnlyOwnedUnexpiredUploadsWithMatchingOssMetadata() throws Throwable {
        FileAsset asset = tempAsset(900L, 42L, 1024L, "image/png");
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(1024L);
        metadata.setContentType("image/png");
        FileAssetMapper mapper = assetMapper();
        when(mapper.selectById(900L)).thenReturn(asset);
        when(mapper.updateById(asset)).thenReturn(1);
        OSS oss = mock(OSS.class);
        when(oss.doesObjectExist("test-bucket", asset.getObjectKey())).thenReturn(true);
        when(oss.getObjectMetadata("test-bucket", asset.getObjectKey())).thenReturn(metadata);
        Object service = confirmationService(mapper, oss);

        invokeConfirm(service, 42L, 900L);

        assertNotNull(asset.getUploadedAt(), "确认上传后必须记录上传时间");
        verify(mapper).updateById(asset);
    }

    @Test
    void rejectsConfirmationForAnotherOwner() throws Throwable {
        FileAsset asset = tempAsset(901L, 42L, 1024L, "image/png");
        FileAssetMapper mapper = assetMapper();
        when(mapper.selectById(901L)).thenReturn(asset);
        OSS oss = mock(OSS.class);
        Object service = confirmationService(mapper, oss);

        assertThrows(BizException.class, () -> invokeConfirm(service, 99L, 901L));
        verify(oss, never()).doesObjectExist(any(), any());
        verify(mapper, never()).updateById(any(FileAsset.class));
    }

    @Test
    void rejectsMissingExpiredOrMetadataMismatchedUploads() throws Throwable {
        FileAsset missing = tempAsset(902L, 42L, 1024L, "image/png");
        FileAssetMapper missingMapper = assetMapper();
        when(missingMapper.selectById(902L)).thenReturn(missing);
        OSS missingOss = mock(OSS.class);
        when(missingOss.doesObjectExist("test-bucket", missing.getObjectKey())).thenReturn(false);
        assertThrows(BizException.class, () -> invokeConfirm(
                confirmationService(missingMapper, missingOss), 42L, 902L));

        FileAsset expired = tempAsset(903L, 42L, 1024L, "image/png");
        expired.setCreatedAt(LocalDateTime.now().minusDays(4));
        FileAssetMapper expiredMapper = assetMapper();
        when(expiredMapper.selectById(903L)).thenReturn(expired);
        assertThrows(BizException.class, () -> invokeConfirm(
                confirmationService(expiredMapper, mock(OSS.class)), 42L, 903L));

        FileAsset mismatched = tempAsset(904L, 42L, 1024L, "image/png");
        FileAssetMapper mismatchedMapper = assetMapper();
        when(mismatchedMapper.selectById(904L)).thenReturn(mismatched);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(2048L);
        metadata.setContentType("image/jpeg");
        OSS mismatchedOss = mock(OSS.class);
        when(mismatchedOss.doesObjectExist("test-bucket", mismatched.getObjectKey())).thenReturn(true);
        when(mismatchedOss.getObjectMetadata("test-bucket", mismatched.getObjectKey())).thenReturn(metadata);
        assertThrows(BizException.class, () -> invokeConfirm(
                confirmationService(mismatchedMapper, mismatchedOss), 42L, 904L));
        verify(mismatchedMapper, never()).updateById(any(FileAsset.class));
    }

    @Test
    void repeatedConfirmationIsIdempotent() throws Throwable {
        FileAsset asset = tempAsset(905L, 42L, 1024L, "image/png");
        asset.setUploadedAt(LocalDateTime.now().minusSeconds(1));
        FileAssetMapper mapper = assetMapper();
        when(mapper.selectById(905L)).thenReturn(asset);
        Object service = confirmationService(mapper, mock(OSS.class));

        invokeConfirm(service, 42L, 905L);

        verify(mapper, never()).updateById(any(FileAsset.class));
    }

    private FileAssetMapper assetMapper() {
        FileAssetMapper mapper = mock(FileAssetMapper.class);
        when(mapper.insert(any(FileAsset.class))).thenAnswer(invocation -> {
            FileAsset asset = invocation.getArgument(0);
            asset.setId(1234L);
            return 1;
        });
        return mapper;
    }

    private OssUploadPolicyService policyService(FileAssetMapper mapper) {
        OssUploadPolicyService service = new OssUploadPolicyService();
        ReflectionTestUtils.setField(service, "accessKeyId", "test-access-key");
        ReflectionTestUtils.setField(service, "accessKeySecret", "test-secret");
        ReflectionTestUtils.setField(service, "bucketName", "test-bucket");
        OssUrlHelper urlHelper = mock(OssUrlHelper.class);
        when(urlHelper.getUploadHost()).thenReturn("https://test-bucket.oss-cn-test.aliyuncs.com");
        when(urlHelper.getPublicBaseUrl()).thenReturn("https://cdn.example.test");
        ReflectionTestUtils.setField(service, "ossUrlHelper", urlHelper);
        setFieldIfPresent(service, "fileAssetMapper", mapper);
        return service;
    }

    private Object uploadRequest(String scene, String fileName, String mimeType, long fileSize) throws Exception {
        Class<?> requestClass = loadClass(DTO_CLASS, "上传票据请求 DTO 必须存在");
        Object request = requestClass.getDeclaredConstructor().newInstance();
        ReflectionTestUtils.setField(request, "scene", scene);
        ReflectionTestUtils.setField(request, "originalFilename", fileName);
        ReflectionTestUtils.setField(request, "contentType", mimeType);
        ReflectionTestUtils.setField(request, "fileSize", fileSize);
        return request;
    }

    private MockHttpServletRequest authenticatedRequest(Long userId, List<String> roles) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthContext.ATTR_USER_ID, String.valueOf(userId));
        request.setAttribute(AuthContext.ATTR_ROLES, roles);
        return request;
    }

    private Object invokeTicket(OssUploadPolicyService service, Object request, MockHttpServletRequest httpRequest)
            throws Throwable {
        Method method = OssUploadPolicyService.class.getMethod(
                "createTicket", request.getClass(), jakarta.servlet.http.HttpServletRequest.class);
        return invoke(method, service, request, httpRequest);
    }

    private Object confirmationService(FileAssetMapper mapper, OSS oss) throws Exception {
        Class<?> serviceClass = loadClass(CONFIRMATION_SERVICE_CLASS, "上传确认服务必须存在");
        Object service = serviceClass.getDeclaredConstructor().newInstance();
        setFieldIfPresent(service, "fileAssetMapper", mapper);
        setFieldIfPresent(service, "ossClient", oss);
        setFieldIfPresent(service, "bucketName", "test-bucket");
        return service;
    }

    private FileAsset tempAsset(Long assetId, Long ownerId, Long fileSize, String contentType) {
        FileAsset asset = new FileAsset();
        asset.setId(assetId);
        asset.setObjectKey("tmp/meme/42/20261011/" + assetId + ".png");
        asset.setOwnerId(ownerId);
        asset.setScene("meme");
        asset.setContentType(contentType);
        asset.setFileSize(fileSize);
        asset.setStatus("TEMP");
        asset.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        return asset;
    }

    private void invokeConfirm(Object service, Long ownerId, Long assetId) throws Throwable {
        Method method = service.getClass().getMethod("confirmUpload", Long.class, Long.class);
        invoke(method, service, ownerId, assetId);
    }

    private Object invoke(Method method, Object target, Object... args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private Class<?> loadClass(String name, String missingMessage) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            assertNotNull(null, missingMessage);
            throw new AssertionError(missingMessage, exception);
        }
    }

    private void setFieldIfPresent(Object target, String fieldName, Object value) {
        if (org.springframework.util.ReflectionUtils.findField(target.getClass(), fieldName) != null) {
            ReflectionTestUtils.setField(target, fieldName, value);
        }
    }
}
