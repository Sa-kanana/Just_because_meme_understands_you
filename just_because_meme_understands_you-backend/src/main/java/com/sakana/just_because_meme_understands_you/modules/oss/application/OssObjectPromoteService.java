package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.aliyun.oss.OSS;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
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
    /** 中文注释：上传票据新格式中梗图场景使用的单数临时目录。 */
    public static final String TMP_MEME = "tmp/meme/";
    /** 中文注释：上传票据新格式中评论图片场景使用的单数临时目录。 */
    public static final String TMP_COMMENT = "tmp/comment/";

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Value("${oss.bucketName:}")
    private String bucketName;

    @Resource
    private OSS ossClient;

    @Resource
    private OssUrlHelper ossUrlHelper;

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
        assertTmpOwnedByUser(key, userId, TMP_AVATAR);
        String dest = permanentPrefix + fileNameOf(key);
        //复制objectkey到正式前缀目录
        return copyTemporaryObject(key, dest, permanentPrefix);
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
        assertTmpOwnedByUser(key, userId, TMP_MEMES, TMP_MEME);
        String dest = "memes/" + LocalDate.now().format(DATE_DIR) + "/" + fileNameOf(key);
        return copyTemporaryObject(key, dest, "memes/");
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
        assertTmpOwnedByUser(key, userId, TMP_COMMENTS, TMP_COMMENT);
        String dest = "comments/" + LocalDate.now().format(DATE_DIR) + "/" + fileNameOf(key);
        return copyTemporaryObject(key, dest, "comments/");
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
        assertTmpOwnedByUser(key, userId, TMP_HOME);
        String dest = "home/" + LocalDate.now().format(DATE_DIR) + "/" + fileNameOf(key);
        return copyTemporaryObject(key, dest, "home/");
    }

    /** 中文注释：复制已确认的临时对象；源对象由临时资产清理任务和 OSS 生命周期处理。 */
    private String copyTemporaryObject(String sourceKey, String destKey, String assertPrefix) {
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
        return destKey;
    }

    private void assertTmpOwnedByUser(String key, Long userId, String... tmpTypePrefixes) {
        for (String tmpTypePrefix : tmpTypePrefixes) {
            String expected = tmpTypePrefix + userId + "/";
            if (key.startsWith(expected)) {
                ossUrlHelper.assertOwnedImageKey(key, expected);
                return;
            }
        }
        throw new BizException(Result.CODE_BAD_REQUEST, "临时图片不属于当前用户或已失效");
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
