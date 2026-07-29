package com.sakana.just_because_meme_understands_you.service.oss;

import com.aliyun.oss.OSS;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AfterCommitExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 将 tmp/ 直传对象提升为业务正式前缀，配合 OSS 生命周期清理未提交的临时图。
 * <p>
 * 直传目录：tmp/{avatar|memes|comments}/{userId}/…
 * 正式目录：avatar/{userId}/、memes/{yyyyMMdd}/、comments/{yyyyMMdd}/
 * 已在正式前缀的历史 key 原样返回，兼容旧数据。
 */
@Slf4j
@Service
public class OssObjectPromoteService {

    public static final String TMP_PREFIX = "tmp/";
    public static final String TMP_AVATAR = "tmp/avatar/";
    public static final String TMP_MEMES = "tmp/memes/";
    public static final String TMP_COMMENTS = "tmp/comments/";
    public static final String TMP_HOME = "tmp/home/";

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${oss.bucketName:}")
    private String bucketName;

    @Resource
    private OSS ossClient;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Resource
    private AfterCommitExecutor afterCommitExecutor;

    /**
     * 头像：tmp/avatar/{userId}/… → avatar/{userId}/…
     */
    public String promoteAvatar(String keyOrUrl, Long userId) {
        requireUserId(userId);
        String key = requireOwnedKey(keyOrUrl);
        String permanentPrefix = "avatar/" + userId + "/";
        if (key.startsWith(permanentPrefix)) {
            ossUrlHelper.assertOwnedImageKey(key, permanentPrefix);
            return key;
        }
        assertTmpOwnedByUser(key, TMP_AVATAR, userId);
        String dest = permanentPrefix + fileNameOf(key);
        return copyAndScheduleDeleteTmp(key, dest, permanentPrefix);
    }

    /**
     * 梗图：tmp/memes/{userId}/… → memes/{yyyyMMdd}/…
     * 已在 memes/ 的历史 key 直接通过。
     */
    public String promoteMemeImage(String keyOrUrl, Long userId) {
        requireUserId(userId);
        String key = requireOwnedKey(keyOrUrl);
        if (key.startsWith("memes/")) {
            ossUrlHelper.assertOwnedImageKey(key, "memes/");
            return key;
        }
        assertTmpOwnedByUser(key, TMP_MEMES, userId);
        String dest = "memes/" + LocalDate.now().format(DATE_DIR) + "/" + fileNameOf(key);
        return copyAndScheduleDeleteTmp(key, dest, "memes/");
    }

    /**
     * 评论图：tmp/comments/{userId}/… → comments/{yyyyMMdd}/…
     */
    public String promoteCommentImage(String keyOrUrl, Long userId) {
        requireUserId(userId);
        String key = requireOwnedKey(keyOrUrl);
        if (key.startsWith("comments/")) {
            ossUrlHelper.assertOwnedImageKey(key, "comments/");
            return key;
        }
        assertTmpOwnedByUser(key, TMP_COMMENTS, userId);
        String dest = "comments/" + LocalDate.now().format(DATE_DIR) + "/" + fileNameOf(key);
        return copyAndScheduleDeleteTmp(key, dest, "comments/");
    }

    /**
     * 首页轮播：tmp/home/{userId}/… → home/{yyyyMMdd}/…；已在 home/ 的原样返回。
     */
    public String promoteHomeImage(String keyOrUrl, Long userId) {
        requireUserId(userId);
        String key = requireOwnedKey(keyOrUrl);
        if (key.startsWith("home/")) {
            ossUrlHelper.assertOwnedImageKey(key, "home/");
            return key;
        }
        assertTmpOwnedByUser(key, TMP_HOME, userId);
        String dest = "home/" + LocalDate.now().format(DATE_DIR) + "/" + fileNameOf(key);
        return copyAndScheduleDeleteTmp(key, dest, "home/");
    }

    /**
     * 梗附属媒体：允许 tmp/memes、正式 memes/home/common；外链原样返回。
     */
    public String promoteMemeMediaOrKeepExternal(String keyOrUrl, Long userId) {
        if (!StringUtils.hasText(keyOrUrl)) {
            return keyOrUrl;
        }
        String normalized = ossUrlHelper.normalizeForStorage(keyOrUrl);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return normalized;
        }
        if (normalized.startsWith("home/") || normalized.startsWith("common/")) {
            ossUrlHelper.assertOwnedImageKey(normalized, "home/", "common/");
            return normalized;
        }
        return promoteMemeImage(normalized, userId);
    }

    private String copyAndScheduleDeleteTmp(String sourceKey, String destKey, String assertPrefix) {
        if (!StringUtils.hasText(bucketName)) {
            throw new BizException(Result.CODE_ERROR, "未配置 OSS bucketName");
        }
        if (sourceKey.equals(destKey)) {
            ossUrlHelper.assertOwnedImageKey(destKey, assertPrefix);
            return destKey;
        }
        try {
            if (!ossClient.doesObjectExist(bucketName, sourceKey)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "临时图片不存在或已过期，请重新上传");
            }
            ossClient.copyObject(bucketName, sourceKey, bucketName, destKey);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OSS tmp promote 失败, source={}, dest={}", sourceKey, destKey, e);
            throw new BizException(Result.CODE_ERROR, "图片确认失败，请稍后重试");
        }
        ossUrlHelper.assertOwnedImageKey(destKey, assertPrefix);
        afterCommitExecutor.execute(() -> deleteQuietly(sourceKey));
        return destKey;
    }

    private void deleteQuietly(String key) {
        try {
            ossClient.deleteObject(bucketName, key);
        } catch (Exception e) {
            log.warn("OSS tmp 删除失败（可由生命周期兜底）, key={}", key, e);
        }
    }

    private void assertTmpOwnedByUser(String key, String tmpTypePrefix, Long userId) {
        String expected = tmpTypePrefix + userId + "/";
        if (!key.startsWith(expected)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "临时图片不属于当前用户或已失效");
        }
        ossUrlHelper.assertOwnedImageKey(key, expected);
    }

    private String requireOwnedKey(String keyOrUrl) {
        String key = ossUrlHelper.normalizeForStorage(keyOrUrl);
        if (!StringUtils.hasText(key)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "图片地址不能为空");
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            throw new BizException(Result.CODE_BAD_REQUEST, "图片须先上传到 OSS");
        }
        return key;
    }

    private static void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
    }

    private static String fileNameOf(String key) {
        int idx = key.lastIndexOf('/');
        String name = idx >= 0 ? key.substring(idx + 1) : key;
        if (!StringUtils.hasText(name)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "图片地址不合法");
        }
        return name;
    }
}
