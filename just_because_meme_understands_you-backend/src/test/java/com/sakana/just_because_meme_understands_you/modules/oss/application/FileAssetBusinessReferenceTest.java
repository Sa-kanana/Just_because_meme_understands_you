package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.aliyun.oss.OSS;
import com.sakana.just_because_meme_understands_you.modules.comment.application.support.MemeCommentSupport;
import com.sakana.just_because_meme_understands_you.modules.comment.domain.entity.MemeCommentImage;
import com.sakana.just_because_meme_understands_you.modules.comment.infrastructure.persistence.mapper.MemeCommentImageMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetDeleteTask;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetReference;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetDeleteTaskMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetReferenceMapper;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.impl.UserProfileServiceImpl;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/** 中文注释：验证业务行引用切换、服务端头像上传登记及评论图片行引用维护。 */
class FileAssetBusinessReferenceTest {

    @Test
    void activatesNewAssetAndMovesUniqueBusinessReferenceBeforeOldAssetIsQueued() {
        FileAsset oldAsset = activeAsset(1L, "avatar/7/old.png");
        FileAsset newAsset = activeAsset(2L, "avatar/7/new.png");
        FileAssetReference oldReference = reference(31L, 1L, "USER", 7L, "AVATAR");
        FileAssetMapper assetMapper = mock(FileAssetMapper.class);
        when(assetMapper.selectByObjectKey(newAsset.getObjectKey())).thenReturn(newAsset);
        when(assetMapper.selectByIdForUpdate(1L)).thenReturn(oldAsset);
        when(assetMapper.selectByIdForUpdate(2L)).thenReturn(newAsset);
        when(assetMapper.updateById(newAsset)).thenReturn(1);
        when(assetMapper.updateById(oldAsset)).thenReturn(1);
        FileAssetReferenceMapper referenceMapper = mock(FileAssetReferenceMapper.class);
        when(referenceMapper.selectByBusinessFieldForUpdate("USER", 7L, "AVATAR"))
                .thenReturn(List.of(oldReference));
        when(referenceMapper.updateById(oldReference)).thenReturn(1);
        when(referenceMapper.countByAssetId(1L)).thenReturn(0L);
        FileAssetDeleteTaskMapper deleteTaskMapper = mock(FileAssetDeleteTaskMapper.class);
        when(deleteTaskMapper.insert(any(FileAssetDeleteTask.class))).thenReturn(1);
        OssUrlHelper urlHelper = mock(OssUrlHelper.class);
        when(urlHelper.normalizeForStorage(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        FileAssetService service = new FileAssetService();
        ReflectionTestUtils.setField(service, "cleanupProperties", new OssCleanupProperties());
        ReflectionTestUtils.setField(service, "fileAssetMapper", assetMapper);
        ReflectionTestUtils.setField(service, "fileAssetReferenceMapper", referenceMapper);
        ReflectionTestUtils.setField(service, "fileAssetDeleteTaskMapper", deleteTaskMapper);
        ReflectionTestUtils.setField(service, "ossUrlHelper", urlHelper);

        service.replaceReference(newAsset.getObjectKey(), "USER", 7L, "AVATAR", "AVATAR_REPLACED");

        InOrder order = inOrder(assetMapper, referenceMapper, deleteTaskMapper);
        order.verify(assetMapper).updateById(newAsset);
        order.verify(referenceMapper).updateById(oldReference);
        order.verify(referenceMapper).countByAssetId(1L);
        order.verify(assetMapper).updateById(oldAsset);
        order.verify(deleteTaskMapper).insert(any(FileAssetDeleteTask.class));
        assertEquals(2L, oldReference.getAssetId());
        assertEquals("PENDING_DELETE", oldAsset.getStatus());
        assertEquals("ACTIVE", newAsset.getStatus());
        verify(referenceMapper, never()).deleteById(31L);
    }

    @Test
    void savesCommentImageReferenceUsingTheGeneratedImageRowId() {
        FileAssetService fileAssetService = mock(FileAssetService.class);
        when(fileAssetService.promote("tmp/comment/7/20261011/image.png", 7L, "comment"))
                .thenReturn("comments/20261011/image.png");
        MemeCommentImageMapper imageMapper = mock(MemeCommentImageMapper.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            MemeCommentImage image = invocation.getArgument(0);
            image.setId(91L);
            return 1;
        }).when(imageMapper).insert(any(MemeCommentImage.class));
        MemeCommentSupport support = new MemeCommentSupport();
        ReflectionTestUtils.setField(support, "memeCommentImageMapper", imageMapper);
        setFieldIfPresent(support, "fileAssetService", fileAssetService);

        support.saveImages(55L, 7L, List.of("tmp/comment/7/20261011/image.png"));

        InOrder order = inOrder(imageMapper, fileAssetService);
        order.verify(imageMapper).insert(any(MemeCommentImage.class));
        order.verify(fileAssetService).replaceReference(
                "comments/20261011/image.png", "COMMENT_IMAGE", 91L, "URL", "COMMENT_IMAGE_CREATED");
    }

    @Test
    void repeatedCommentImageUsesOneTemporaryPromotionAndTwoRowReferences() {
        String temporaryKey = "tmp/comment/7/shared.png";
        FileAssetService fileAssetService = mock(FileAssetService.class);
        when(fileAssetService.promote(temporaryKey, 7L, "comment")).thenReturn("comments/shared.png");
        MemeCommentImageMapper imageMapper = mock(MemeCommentImageMapper.class);
        java.util.concurrent.atomic.AtomicLong ids = new java.util.concurrent.atomic.AtomicLong(91L);
        org.mockito.Mockito.doAnswer(invocation -> {
            ((MemeCommentImage) invocation.getArgument(0)).setId(ids.getAndIncrement());
            return 1;
        }).when(imageMapper).insert(any(MemeCommentImage.class));
        MemeCommentSupport support = new MemeCommentSupport();
        ReflectionTestUtils.setField(support, "memeCommentImageMapper", imageMapper);
        ReflectionTestUtils.setField(support, "fileAssetService", fileAssetService);

        support.saveImages(55L, 7L, List.of(temporaryKey, temporaryKey));

        verify(fileAssetService, times(1)).promote(temporaryKey, 7L, "comment");
        verify(fileAssetService).replaceReference("comments/shared.png", "COMMENT_IMAGE", 91L, "URL",
                "COMMENT_IMAGE_CREATED");
        verify(fileAssetService).replaceReference("comments/shared.png", "COMMENT_IMAGE", 92L, "URL",
                "COMMENT_IMAGE_CREATED");
    }

    @Test
    void registersServerUploadedAvatarAsConfirmedTemporaryAsset() throws Exception {
        Long userId = 7L;
        IUserService userService = mock(IUserService.class);
        User user = new User();
        user.setId(userId);
        when(userService.getById(userId)).thenReturn(user);
        FileAssetService fileAssetService = mock(FileAssetService.class);
        OSS ossClient = mock(OSS.class);
        OssUrlHelper urlHelper = mock(OssUrlHelper.class);
        when(urlHelper.toPublicUrl(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        UserProfileServiceImpl profileService = new UserProfileServiceImpl();
        ReflectionTestUtils.setField(profileService, "userService", userService);
        ReflectionTestUtils.setField(profileService, "ossClient", ossClient);
        ReflectionTestUtils.setField(profileService, "ossUrlHelper", urlHelper);
        ReflectionTestUtils.setField(profileService, "bucketName", "test-bucket");
        setFieldIfPresent(profileService, "fileAssetService", fileAssetService);
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});

        profileService.uploadAvatar(userId, file);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileAssetService).registerServerUploadedTempAsset(
                eq(userId), keyCaptor.capture(), eq("avatar"), eq("avatar.png"), eq("image/png"), eq(3L));
        assertTrue(keyCaptor.getValue().startsWith("tmp/avatar/7/"));
        assertTrue(keyCaptor.getValue().endsWith(".png"));
        verify(ossClient).putObject(eq("test-bucket"), eq(keyCaptor.getValue()), any(InputStream.class));
    }

    private FileAsset activeAsset(Long id, String key) {
        FileAsset asset = new FileAsset();
        asset.setId(id);
        asset.setObjectKey(key);
        asset.setOwnerId(7L);
        asset.setScene("avatar");
        asset.setStatus("ACTIVE");
        return asset;
    }

    private FileAssetReference reference(Long id, Long assetId, String type, Long bizId, String fieldKey) {
        FileAssetReference reference = new FileAssetReference();
        reference.setId(id);
        reference.setAssetId(assetId);
        reference.setBizType(type);
        reference.setBizId(bizId);
        reference.setFieldKey(fieldKey);
        return reference;
    }

    private void setFieldIfPresent(Object target, String fieldName, Object value) {
        if (org.springframework.util.ReflectionUtils.findField(target.getClass(), fieldName) != null) {
            ReflectionTestUtils.setField(target, fieldName, value);
        }
    }
}
