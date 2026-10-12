package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.sakana.just_because_meme_understands_you.api.user.dto.OssUploadTicketRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.OssUploadPolicyVO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.modules.auth.domain.AuthConstants;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.FileAssetStatus;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper.FileAssetMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** 中文注释：校验上传声明、登记临时资产并签发限定到单一对象键的 OSS PostObject 票据。 */
@Slf4j
@Service
public class OssUploadPolicyService {

    /** 中文注释：PostObject 上传票据的有效秒数。 */
    private static final long POLICY_TTL_SECONDS = 60L;

    /** 中文注释：允许上传的最大文件字节数，覆盖头像、梗图和评论图片。 */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    /** 中文注释：临时对象键中的日期目录格式。 */
    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 中文注释：用于兼容现有 OSS PostObject 凭证格式的 HMAC 算法。 */
    private static final String HMAC_SHA1 = "HmacSHA1";

    /** 中文注释：允许客户端申请票据的业务场景白名单。 */
    private static final Set<String> ALLOWED_SCENES = Set.of("avatar", "meme", "comment", "home");

    /** 中文注释：支持的图片文件扩展名与规范 MIME 类型映射。 */
    private static final Map<String, String> IMAGE_MIME_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif");

    /** 中文注释：阿里云 OSS AccessKey ID 配置。 */
    @Value("${oss.accessKeyId}")
    private String accessKeyId;

    /** 中文注释：用于服务器端签名的阿里云 OSS AccessKey Secret 配置。 */
    @Value("${oss.accessKeySecret}")
    private String accessKeySecret;

    /** 中文注释：存储上传对象的 OSS Bucket 名称配置。 */
    @Value("${oss.bucketName}")
    private String bucketName;

    /** 中文注释：用于将临时资产登记到数据库的文件资产映射器。 */
    @Resource
    private FileAssetMapper fileAssetMapper;

    /** 中文注释：提供 OSS 上传域名和公开访问域名的 URL 工具。 */
    @Resource
    private OssUrlHelper ossUrlHelper;

    /**
     * 中文注释：校验登录身份、上传场景、文件名、MIME 和大小后创建临时资产及限时票据。
     *
     * @param request 客户端声明的上传场景和文件元数据
     * @param httpRequest 当前认证请求，用于确认所有者和首页图上传权限
     * @return 已登记资产对应的 OSS 直传票据
     */
    public OssUploadPolicyVO createTicket(OssUploadTicketRequestDTO request, HttpServletRequest httpRequest) {
        Long ownerId = AuthContext.requireCurrentUserId(httpRequest);
        if (!StringUtils.hasText(bucketName)) {
            throw new BizException(Result.CODE_ERROR, "未配置 OSS bucketName");
        }

        String scene = normalizeScene(request);
        authorizeScene(scene, httpRequest);
        String originalFilename = normalizeFilename(request.getOriginalFilename());
        String extension = resolveAndValidateImageType(originalFilename, request.getContentType());
        Long fileSize = request.getFileSize();
        if (fileSize == null || fileSize <= 0 || fileSize > MAX_FILE_SIZE) {
            throw new BizException(Result.CODE_BAD_REQUEST, "图片大小必须大于0且不能超过10MiB");
        }

        LocalDateTime now = LocalDateTime.now();
        String objectKey = "tmp/" + scene + "/" + ownerId + "/"
                + LocalDate.now().format(DATE_DIR_FORMATTER) + "/"
                + UUID.randomUUID() + "." + extension;
        Date expiration = new Date(System.currentTimeMillis() + POLICY_TTL_SECONDS * 1000);
        String contentType = normalizeMimeType(request.getContentType());
        String policyBase64 = Base64.getEncoder().encodeToString(
                buildPolicyJson(expiration, objectKey, contentType).getBytes(StandardCharsets.UTF_8));
        String signature = sign(policyBase64);

        FileAsset asset = new FileAsset();
        asset.setObjectKey(objectKey);
        asset.setOwnerId(ownerId);
        asset.setScene(scene);
        asset.setOriginalFilename(originalFilename);
        asset.setContentType(contentType);
        asset.setFileSize(fileSize);
        asset.setStatus(FileAssetStatus.TEMP.name());
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        if (fileAssetMapper.insert(asset) != 1 || asset.getId() == null) {
            throw new BizException(Result.CODE_ERROR, "创建上传资产失败，请稍后重试");
        }

        OssUploadPolicyVO vo = new OssUploadPolicyVO();
        vo.setAssetId(asset.getId());
        vo.setObjectKey(objectKey);
        vo.setAccessKeyId(accessKeyId);
        vo.setPolicy(policyBase64);
        vo.setSignature(signature);
        vo.setHost(ossUrlHelper.getUploadHost());
        vo.setPublicBaseUrl(ossUrlHelper.getPublicBaseUrl());
        vo.setExpire(expiration.getTime() / 1000);
        return vo;
    }

    /** 中文注释：校验并规范化上传场景名称。 */
    private String normalizeScene(OssUploadTicketRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getScene())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "上传场景不能为空");
        }
        String scene = request.getScene().trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCENES.contains(scene)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "不支持该上传场景");
        }
        return scene;
    }

    /** 中文注释：首页素材仅允许当前请求携带管理员角色时签发上传票据。 */
    private void authorizeScene(String scene, HttpServletRequest request) {
        if (!"home".equals(scene)) {
            return;
        }
        String legacyRole = AuthContext.currentRole(request);
        boolean admin = AuthContext.currentRoles(request).contains("ADMIN")
                || AuthContext.currentRoles(request).contains("SUPER_ADMIN")
                || AuthConstants.ROLE_ADMIN.equals(legacyRole)
                || AuthConstants.ROLE_SUPER_ADMIN.equals(legacyRole);
        if (!admin) {
            throw new BizException(Result.CODE_FORBIDDEN, "无权上传该类型文件");
        }
    }

    /** 中文注释：提取文件名并剔除客户端路径，避免把本机目录信息写入资产记录。 */
    private String normalizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "文件名不能为空");
        }
        String normalized = filename.trim().replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        boolean containsControlCharacter = normalized.codePoints().anyMatch(Character::isISOControl);
        if (!StringUtils.hasText(normalized) || normalized.length() > 255 || containsControlCharacter) {
            throw new BizException(Result.CODE_BAD_REQUEST, "文件名不合法");
        }
        return normalized;
    }

    /** 中文注释：只允许受支持的图片扩展名和与其匹配的 MIME 类型。 */
    private String resolveAndValidateImageType(String filename, String declaredMimeType) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new BizException(Result.CODE_BAD_REQUEST, "图片须为 JPG、PNG、WebP 或 GIF 格式");
        }
        String extension = filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        String expectedMimeType = IMAGE_MIME_TYPES.get(extension);
        String normalizedMimeType = normalizeMimeType(declaredMimeType);
        if (expectedMimeType == null || !expectedMimeType.equals(normalizedMimeType)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "图片扩展名与 MIME 类型不匹配或格式不受支持");
        }
        return extension;
    }

    /** 中文注释：统一 MIME 类型大小写并移除参数，避免声明格式绕过白名单。 */
    private String normalizeMimeType(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "图片 MIME 类型不能为空");
        }
        return mimeType.trim().toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
    }

    /** 中文注释：生成只允许精确对象键、精确 MIME 和指定最大对象大小的 PostObject policy。 */
    private String buildPolicyJson(Date expiration, String objectKey, String contentType) {
        String expirationIso = toIso8601Utc(expiration);
        return "{\"expiration\":\"" + expirationIso + "\",\"conditions\":["
                + "[\"content-length-range\",1," + MAX_FILE_SIZE + "],"
                + "[\"eq\",\"$key\",\"" + objectKey + "\"],"
                + "[\"eq\",\"$Content-Type\",\"" + contentType + "\"]]}";
    }

    /** 中文注释：使用 OSS PostObject 兼容的 HMAC-SHA1 算法签名 policy。 */
    private String sign(String policyBase64) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(
                    Objects.requireNonNull(accessKeySecret).getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA1));
            return Base64.getEncoder().encodeToString(mac.doFinal(policyBase64.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            log.warn("生成 OSS 上传签名失败", exception);
            throw new BizException(Result.CODE_ERROR, "获取上传凭证失败，请稍后重试");
        }
    }

    /** 中文注释：格式化为 OSS policy 要求的 ISO 8601 UTC 时间。 */
    private String toIso8601Utc(Date date) {
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        formatter.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return formatter.format(date);
    }
}
