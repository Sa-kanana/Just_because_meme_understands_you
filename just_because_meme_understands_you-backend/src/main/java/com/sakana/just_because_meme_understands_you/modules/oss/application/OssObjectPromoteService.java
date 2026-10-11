package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.aliyun.oss.OSS;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.transaction.AfterCommitExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

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
        //校验userId不能为空
        requireUserId(userId);
        //标准化objectkey
        String key = requireOwnedKey(keyOrUrl);
        String permanentPrefix = "avatar/" + userId + "/";
        if (key.startsWith(permanentPrefix)) {
            //如果objectkey已存在正式前缀，直接返回
            ossUrlHelper.assertOwnedImageKey(key, permanentPrefix);
            return key;
        }
        //校验objectkey是否属于tmp/avatar目录
        assertTmpOwnedByUser(key, TMP_AVATAR, userId);
        String dest = permanentPrefix + fileNameOf(key);
        //复制objectkey到正式前缀目录
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

    /** 中文注释：事务提交后清理已不再引用的梗图对象，只允许删除 memes/ 前缀下的对象。 */
    public void deleteMemeObjectsAfterCommit(Collection<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return;
        }
        Set<String> safeKeys = new LinkedHashSet<>();
        for (String objectKey : objectKeys) {
            String normalized = ossUrlHelper.normalizeForStorage(objectKey);
            if (isSafeMemeObjectKey(normalized)) {
                safeKeys.add(normalized);
            } else if (StringUtils.hasText(objectKey)) {
                log.warn("忽略非梗图目录或不安全的 OSS 清理请求, key={}", objectKey);
            }
        }
        if (safeKeys.isEmpty()) {
            return;
        }
        afterCommitExecutor.execute(() -> safeKeys.forEach(this::deleteMemeObjectQuietly));
    }

    /** 同步删除 home/ 目录下的轮播图对象，失败时由调用方回滚轮播图记录删除。 */
    public void deleteHomeImage(String objectKeyOrUrl) {
        if (!StringUtils.hasText(objectKeyOrUrl)) {
            return;
        }
        String objectKey = ossUrlHelper.normalizeForStorage(objectKeyOrUrl);
        String lowerKey = objectKey == null ? "" : objectKey.toLowerCase(Locale.ROOT);
        if (lowerKey.startsWith("http://") || lowerKey.startsWith("https://")) {
            return;
        }
        if (!isSafeHomeImageObjectKey(objectKey)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "轮播图 OSS 图片地址不合法，无法删除");
        }
        ossUrlHelper.assertOwnedImageKey(objectKey, "home/");
        if (!StringUtils.hasText(bucketName)) {
            throw new BizException(Result.CODE_ERROR, "未配置 OSS bucketName，无法删除轮播图图片");
        }
        try {
            ossClient.deleteObject(bucketName, objectKey);
        } catch (Exception exception) {
            log.warn("轮播图 OSS 对象删除失败, key={}", objectKey, exception);
            throw new BizException(Result.CODE_ERROR, "轮播图图片删除失败，请稍后重试");
        }
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
            // ① 校验 sourceKey 在 OSS 中存在
            if (!ossClient.doesObjectExist(bucketName, sourceKey)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "临时图片不存在或已过期，请重新上传");
            }
            // ② OSS 内部复制（同 Bucket，零流量）
            ossClient.copyObject(bucketName, sourceKey, bucketName, destKey);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OSS tmp promote 失败, source={}, dest={}", sourceKey, destKey, e);
            throw new BizException(Result.CODE_ERROR, "图片确认失败，请稍后重试");
        }
        // ③ 校验目标文件合法
        ossUrlHelper.assertOwnedImageKey(destKey, assertPrefix);
        // ④ 事务提交后异步删除临时文件
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

    private boolean isSafeMemeObjectKey(String key) {
        if (!StringUtils.hasText(key) || !key.startsWith("memes/") || key.length() > 1024) {
            return false;
        }
        for (String segment : key.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSafeHomeImageObjectKey(String key) {
        if (!StringUtils.hasText(key) || !key.startsWith("home/") || key.length() > 1024) {
            return false;
        }
        for (String segment : key.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private void deleteMemeObjectQuietly(String key) {
        if (!StringUtils.hasText(bucketName)) {
            log.warn("未配置 OSS bucketName，跳过梗图对象清理 key={}", key);
            return;
        }
        try {
            ossClient.deleteObject(bucketName, key);
        } catch (Exception exception) {
            log.warn("梗图 OSS 对象清理失败（可由后续清理任务兜底）, key={}", key, exception);
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
