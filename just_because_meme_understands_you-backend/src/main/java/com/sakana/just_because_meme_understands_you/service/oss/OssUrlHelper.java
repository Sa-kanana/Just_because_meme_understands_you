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
        String defaultHost = buildDefaultHost();
        if (!StringUtils.hasText(uploadHost)) {
            uploadHost = defaultHost;
        }
        if (!StringUtils.hasText(publicBaseUrl)) {
            publicBaseUrl = defaultHost;
        }
        uploadHost = stripTrailingSlash(uploadHost.trim());
        publicBaseUrl = stripTrailingSlash(publicBaseUrl.trim());

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
        if (!StringUtils.hasText(stored)) {
            return stored;
        }
        String trimmed = stored.trim();
        if (isExternalUrl(trimmed) && !isOwnedObjectUrl(trimmed)) {
            return trimmed;
        }
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
                return extractObjectKey(trimmed);
            }
            return trimmed;
        }
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }

    /**
     * 校验上传后的图片 key 属于本 Bucket 且前缀合法。
     */
    public void assertOwnedImageKey(String keyOrUrl, String... allowedPrefixes) {
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
                return;
            }
        }
        throw new BizException(Result.CODE_BAD_REQUEST, "图片地址不合法");
    }

    public void refreshMemeDetailUrls(MemeDetailVO vo) {
        if (vo == null) {
            return;
        }
        vo.setImage(toPublicUrl(vo.getImage()));
        if (vo.getLinks() == null) {
            return;
        }
        for (MemeResourceVO link : vo.getLinks()) {
            if (link != null && link.getResourceUrl() != null) {
                link.setResourceUrl(toPublicUrls(link.getResourceUrl()));
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
        if (!isExternalUrl(url)) {
            return true;
        }
        try {
            URI uri = URI.create(url);
            return uri.getHost() != null && knownHosts.contains(uri.getHost().toLowerCase(Locale.ROOT));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extractObjectKey(String urlOrKey) {
        if (!StringUtils.hasText(urlOrKey)) {
            return urlOrKey;
        }
        String trimmed = urlOrKey.trim();
        if (!isExternalUrl(trimmed)) {
            return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        }
        try {
            URI uri = URI.create(trimmed);
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
