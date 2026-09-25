package com.sakana.just_because_meme_understands_you.service.oss;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.vo.HomeImageVO;
import com.sakana.just_because_meme_understands_you.vo.MemeDetailVO;
import com.sakana.just_because_meme_understands_you.vo.MemeResourceVO;
import com.sakana.just_because_meme_understands_you.vo.UserFavoriteItemVO;
import com.sakana.just_because_meme_understands_you.vo.UserMemeItemVO;
import com.sakana.just_because_meme_understands_you.vo.UserProfileVO;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * OSS 对象 URL 工具：上传域名与读图域名分离，DB 存 objectKey，展示时拼接 publicBaseUrl。
 * <p>
 * 兼容历史数据中已存的完整 OSS URL；上线切换 CDN 时只需改配置，无需迁移 DB。
 */
@Component
public class OssUrlHelper {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif"
    );

    @Value("${oss.bucketName:}")
    private String bucketName;

    @Value("${oss.endpoint:}")
    private String endpoint;

    /** 前端 PostObject 上传域名，默认 https://{bucket}.{endpoint} */
    @Value("${oss.uploadHost:}")
    private String uploadHost;

    /** 读图访问域名，开发可用 OSS 域名，生产改为 CDN 域名 */
    @Value("${oss.publicBaseUrl:}")
    private String publicBaseUrl;

    private Set<String> knownHosts = Set.of();

    @PostConstruct
    void init() {
        //构建默认域名：https://{bucket}.{endpoint}
        String defaultHost = buildDefaultHost();
        if (!StringUtils.hasText(uploadHost)) {
            uploadHost = defaultHost;
        }
        //未配置上传域名 → 使用默认域名
        if (!StringUtils.hasText(publicBaseUrl)) {
            publicBaseUrl = defaultHost;
        }
        //去除末尾斜杠（统一格式）
        uploadHost = stripTrailingSlash(uploadHost.trim());
        publicBaseUrl = stripTrailingSlash(publicBaseUrl.trim());

        //收集所有已知域名，用于后续判断 URL 归属
        Set<String> hosts = new HashSet<>();
        collectHost(uploadHost, hosts);
        collectHost(publicBaseUrl, hosts);
        collectHost(defaultHost, hosts);
        knownHosts = Set.copyOf(hosts);
    }

    public String getUploadHost() {
        return uploadHost;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    /**
     * 将 DB 中存储的值（objectKey 或历史完整 URL）转为对外可访问 URL。
     * 外部链接（非本 Bucket/CDN）原样返回。
     */
    public String toPublicUrl(String stored) {
        //空值原样返回
        if (!StringUtils.hasText(stored)) {
            return stored;
        }
        //外部链接（非本 Bucket/CDN）原样返回
        String trimmed = stored.trim();
        if (isExternalUrl(trimmed) && !isOwnedObjectUrl(trimmed)) {
            return trimmed;
        }
        //本系统资源，拼接 publicBaseUrl
        String key = extractObjectKey(trimmed);
        if (!StringUtils.hasText(key)) {
            return trimmed;
        }
        return publicBaseUrl + "/" + key;
    }

    public List<String> toPublicUrls(List<String> storedList) {
        if (storedList == null || storedList.isEmpty()) {
            return storedList;
        }
        List<String> result = new ArrayList<>(storedList.size());
        for (String item : storedList) {
            result.add(toPublicUrl(item));
        }
        return result;
    }

    /**
     * 入库前归一化：本 OSS 对象转为 objectKey，外部 http 链接保持完整 URL。
     */
    public String normalizeForStorage(String input) {
        if (!StringUtils.hasText(input)) {
            return input;
        }
        String trimmed = input.trim();
        if (isExternalUrl(trimmed)) {
            if (isOwnedObjectUrl(trimmed)) {
                return extractObjectKey(trimmed); //本系统资源，提取 objectKey 存储到 DB
            }
            return trimmed;
        }
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    /**
     * 校验上传后的图片 key 属于本 Bucket 且前缀合法。
     */
    public void assertOwnedImageKey(String keyOrUrl, String... allowedPrefixes) {
        // 不能为空
        // 不能是外部链接（必须先上传到 OSS）
        // 前缀必须在白名单内
        // 扩展名必须是 .jpg/.png/.webp/.gif
        String key = normalizeForStorage(keyOrUrl);
        if (!StringUtils.hasText(key)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "图片地址不能为空");
        }
        if (isExternalUrl(key)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "图片须先上传到 OSS");
        }
        if (allowedPrefixes == null || allowedPrefixes.length == 0) {
            return;
        }
        for (String prefix : allowedPrefixes) {
            if (StringUtils.hasText(prefix) && key.startsWith(prefix)) {
                assertAllowedImageExtension(key);
                return;
            }
        }
        throw new BizException(Result.CODE_BAD_REQUEST, "图片地址不合法");
    }

    private void assertAllowedImageExtension(String key) {
        int dotIndex = key.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex >= key.length() - 1) {
            throw new BizException(Result.CODE_BAD_REQUEST, "图片须为 JPG、PNG、WebP 或 GIF 格式");
        }
        String extension = key.substring(dotIndex).toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "图片须为 JPG、PNG、WebP 或 GIF 格式");
        }
    }

    public void refreshMemeDetailUrls(MemeDetailVO vo) {
        if (vo == null) {
            return;
        }
        vo.setImage(toPublicUrl(vo.getImage()));
        if (vo.getAuthor() != null) {
            vo.getAuthor().setAvatar(toPublicUrl(vo.getAuthor().getAvatar()));
        }
        if (vo.getLinks() == null) {
            return;
        }
        for (MemeResourceVO link : vo.getLinks()) {
            if (link != null && link.getUrl() != null) {
                link.setUrl(toPublicUrl(link.getUrl()));
            }
        }
    }

    public void refreshHomeImageUrls(List<HomeImageVO> list) {
        if (list == null) {
            return;
        }
        for (HomeImageVO vo : list) {
            if (vo != null) {
                vo.setImgUrl(toPublicUrl(vo.getImgUrl()));
            }
        }
    }

    public void refreshUserProfileUrls(UserProfileVO vo) {
        if (vo == null) {
            return;
        }
        vo.setAvatar(toPublicUrl(vo.getAvatar()));
        if (vo.getMemes() != null) {
            for (UserMemeItemVO item : vo.getMemes()) {
                if (item != null) {
                    item.setImage(toPublicUrl(item.getImage()));
                }
            }
        }
        if (vo.getFavorites() != null) {
            for (UserFavoriteItemVO item : vo.getFavorites()) {
                if (item != null) {
                    item.setImage(toPublicUrl(item.getImage()));
                }
            }
        }
    }

    private String buildDefaultHost() {
        if (!StringUtils.hasText(bucketName) || !StringUtils.hasText(endpoint)) {
            return "";
        }
        return "https://" + bucketName + "." + endpoint;
    }

    private static String stripTrailingSlash(String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        String result = url;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void collectHost(String url, Set<String> hosts) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        try {
            URI uri = URI.create(url.startsWith("http") ? url : "https://" + url);
            if (uri.getHost() != null) {
                hosts.add(uri.getHost().toLowerCase(Locale.ROOT));
            }
        } catch (Exception ignored) {
            // ignore malformed url
        }
    }

    private boolean isExternalUrl(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private boolean isOwnedObjectUrl(String url) {
        // 1. 如果不是外部 URL（比如是相对路径 "/static/images/a.png"）
        //    那它肯定属于自家资源，直接返回 true
        if (!isExternalUrl(url)) {
            return true;
        }
        // 2. 将字符串解析为 URI 对象，提取它的域名（Host）
        try {
            // 3. 提取域名并转为小写（如 "oss.mycompany.com"）
            //    检查这个域名是否存在于事先准备好的已知域名白名单 (knownHosts) 中
            URI uri = new URI(url);
            return uri.getHost() != null && knownHosts.contains(uri.getHost().toLowerCase(Locale.ROOT));
        } catch (Exception ignored) {
            // 4. 如果 URL 格式非法（比如传了个乱码或非标准字符串），解析抛异常，直接视作“非自家资源”
            return false;
        }
    }

    private String extractObjectKey(String urlOrKey) {
        if (!StringUtils.hasText(urlOrKey)) {
            return urlOrKey;
        }
        String trimmed = urlOrKey.trim();
        //非外部链接，直接返回
        if (!isExternalUrl(trimmed)) {
            return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        }
        try {
            //解析为 URI 对象，提取它的路径（Path）
            URI uri = new URI(trimmed);
            String path = uri.getPath();
            if (!StringUtils.hasText(path) || "/".equals(path)) {
                return "";
            }
            return path.startsWith("/") ? path.substring(1) : path;
        } catch (Exception ignored) {
            return trimmed;
        }
    }
}
