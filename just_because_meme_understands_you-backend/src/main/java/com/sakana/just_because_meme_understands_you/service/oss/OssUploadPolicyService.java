package com.sakana.just_because_meme_understands_you.service.oss;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.constant.AuthConstants;
import com.sakana.just_because_meme_understands_you.vo.OssUploadPolicyVO;
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
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * OSS 前端直传凭证签发服务。
 * <p>
 * 采用 PostObject policy 模式：后端生成带前缀限制的 policy 与签名，
 * 前端拿到凭证后直接 POST 表单到 OSS，无需经过后端转发，减轻服务端带宽压力。
 * <p>
 * 直传一律落到 {@code tmp/} 前缀；业务写库时由 {@link OssObjectPromoteService}
 * 复制到正式目录。请在 OSS 控制台为 {@code tmp/} 配置生命周期（建议 3 天过期），
 * 自动清理未提交的临时图。
 *
 * @author sakana
 */
@Slf4j
@Service
public class OssUploadPolicyService {

    /** 凭证有效期 60 秒，防止凭证泄露后被长期滥用 */
    private static final long POLICY_TTL_SECONDS = 60L;

    /** 单文件最大 10MB，覆盖头像、梗图、评论配图等场景 */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    /** 日期目录格式 */
    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** HMAC-SHA1 算法名 */
    private static final String HMAC_SHA1 = "HmacSHA1";

    /** fileType 白名单：映射到 tmp 下的业务子目录 */
    private static final Map<String, String> FILE_TYPE_DIR = new LinkedHashMap<>();

    static {
        FILE_TYPE_DIR.put("avatar", "avatar");
        FILE_TYPE_DIR.put("meme", "memes");
        FILE_TYPE_DIR.put("comment", "comments");
        FILE_TYPE_DIR.put("home", "home");
    }

    /** fileType 兜底目录 */
    private static final String DEFAULT_DIR = "common";

    @Value("${oss.accessKeyId}")
    private String accessKeyId;

    @Value("${oss.accessKeySecret}")
    private String accessKeySecret;

    @Value("${oss.bucketName}")
    private String bucketName;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Resource
    private HttpServletRequest httpServletRequest;

    /**
     * 签发上传凭证。
     *
     * @param fileType 文件类型，决定 tmp 子目录（avatar/meme/comment）
     * @return 直传凭证
     */
    public OssUploadPolicyVO generatePolicy(String fileType) {
        if (!StringUtils.hasText(bucketName)) {
            throw new BizException(Result.CODE_ERROR, "未配置 OSS bucketName");
        }
        String normalized = StringUtils.hasText(fileType) ? fileType.trim().toLowerCase() : DEFAULT_DIR;
        if ("home".equals(normalized)) {
            Long userId = parseCurrentUserId();
            if (userId == null) {
                throw new BizException(Result.CODE_UNAUTHORIZED, "请先登录后再上传");
            }
            Object role = httpServletRequest.getAttribute(AuthConstants.CLAIM_ROLE);
            if (!AuthConstants.ROLE_ADMIN.equals(role == null ? null : String.valueOf(role))) {
                throw new BizException(Result.CODE_FORBIDDEN, "无权上传该类型文件");
            }
        }
        if ("avatar".equals(normalized) || "meme".equals(normalized) || "comment".equals(normalized)) {
            Long userId = parseCurrentUserId();
            if (userId == null) {
                throw new BizException(Result.CODE_UNAUTHORIZED, "请先登录后再上传");
            }
        }
        String dirPrefix = resolveTmpDir(fileType);

        Date expiration = new Date(System.currentTimeMillis() + POLICY_TTL_SECONDS * 1000);
        // policy conditions：限制文件大小与 object key 前缀，防止越权写任意路径
        String policyJson = buildPolicyJson(expiration, dirPrefix);
        String policyBase64 = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
        String signature = sign(policyBase64);

        OssUploadPolicyVO vo = new OssUploadPolicyVO();
        vo.setAccessKeyId(accessKeyId);
        vo.setPolicy(policyBase64);
        vo.setSignature(signature);
        vo.setDir(dirPrefix);
        vo.setHost(ossUrlHelper.getUploadHost());
        vo.setPublicBaseUrl(ossUrlHelper.getPublicBaseUrl());
        vo.setExpire(expiration.getTime() / 1000);
        return vo;
    }

    /**
     * 临时上传目录：tmp/{type}/{userId}/[yyyyMMdd]/
     * 正式入库前需 promote；未提交对象靠 OSS 生命周期删除。
     */
    private String resolveTmpDir(String fileType) {
        String normalized = StringUtils.hasText(fileType) ? fileType.trim().toLowerCase() : DEFAULT_DIR;
        String typeSeg = FILE_TYPE_DIR.getOrDefault(normalized, DEFAULT_DIR);
        Long userId = parseCurrentUserId();
        String userIdSeg = userId != null ? String.valueOf(userId) : "anonymous";
        if ("avatar".equals(normalized)) {
            return OssObjectPromoteService.TMP_PREFIX + typeSeg + "/" + userIdSeg + "/";
        }
        String dateSeg = LocalDate.now().format(DATE_DIR_FORMATTER);
        return OssObjectPromoteService.TMP_PREFIX + typeSeg + "/" + userIdSeg + "/" + dateSeg + "/";
    }

    private Long parseCurrentUserId() {
        Object userId = httpServletRequest.getAttribute("userId");
        if (userId == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(userId));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 构造 PostObject policy JSON。
     * conditions：
     * - content-length-range 限制文件大小
     * - starts-with $key 限制 object key 必须以 dir 开头
     * - starts-with $Content-Type image/ 强制只能上传图片，防恶意 HTML/脚本被当作网页渲染
     * <p>
     * 不再强制 x-oss-object-acl=public-read，上传的 object 继承 Bucket 权限，
     * 由 Bucket ACL 统一控制访问权限，便于运维侧统一调整。
     */
    private String buildPolicyJson(Date expiration, String dir) {
        String expirationIso = toIso8601Utc(expiration);
        // 手动拼接避免引入 Jackson 依赖，字段固定且无用户输入，安全可控
        return "{\"expiration\":\"" + expirationIso + "\","
                + "\"conditions\":["
                + "[\"content-length-range\",0," + MAX_FILE_SIZE + "],"
                + "[\"starts-with\",\"$key\",\"" + dir + "\"],"
                + "[\"starts-with\",\"$Content-Type\",\"image/\"]"
                + "]}";
    }

    /**
     * 计算 HMAC-SHA1 签名并 base64 编码，与 OSS PostObject 签名规则一致。
     */
    private String sign(String policyBase64) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(
                    Objects.requireNonNull(accessKeySecret).getBytes(StandardCharsets.UTF_8),
                    HMAC_SHA1));
            byte[] raw = mac.doFinal(policyBase64.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            log.warn("生成 OSS 上传签名失败", e);
            throw new BizException(Result.CODE_ERROR, "获取上传凭证失败，请稍后重试");
        }
    }

    /**
     * 转为 OSS 要求的 ISO8601 UTC 格式：yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
     */
    private String toIso8601Utc(Date date) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(date);
    }

    /**
     * 暴露 fileType 白名单，供 Controller 校验或前端枚举使用。
     */
    public Set<String> supportedFileTypes() {
        return FILE_TYPE_DIR.keySet();
    }
}
