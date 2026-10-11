package com.sakana.just_because_meme_understands_you.modules.ai.application.support;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 向量灌库幂等哈希：title + introduction + tags。
 */
public final class AiContentHashSupport {

    private AiContentHashSupport() {
    }

    public static String hash(String title, String introduction, List<String> tags) {
        StringBuilder raw = new StringBuilder();
        raw.append(normalize(title)).append('\n');
        raw.append(normalize(introduction)).append('\n');
        List<String> normalizedTags = new ArrayList<>();
        if (tags != null) {
            for (String tag : tags) {
                String t = normalize(tag);
                if (!t.isEmpty()) {
                    normalizedTags.add(t);
                }
            }
        }
        normalizedTags.sort(Comparator.naturalOrder());
        raw.append(String.join(",", normalizedTags));
        return sha256Hex(raw.toString());
    }

    /** 管理员知识库旧版哈希：title + category + content + tags。 */
    public static String knowledgeHash(String title, String category, String content, List<String> tags) {
        StringBuilder raw = new StringBuilder();
        raw.append(normalize(title)).append('\n');
        raw.append(normalize(category)).append('\n');
        raw.append(normalize(content)).append('\n');
        List<String> normalizedTags = new ArrayList<>();
        if (tags != null) {
            for (String tag : tags) {
                String t = normalize(tag);
                if (!t.isEmpty()) {
                    normalizedTags.add(t);
                }
            }
        }
        normalizedTags.sort(Comparator.naturalOrder());
        raw.append(String.join(",", normalizedTags));
        return sha256Hex(raw.toString());
    }

    /**
     * 管理员知识去重指纹：只对提取后的正文计算 SHA-256，忽略元数据。
     * 规范化与数据库回填保持一致：移除 BOM、统一换行符，并保留正文其他空白。
     */
    public static String knowledgeBodySha256(String content) {
        String normalized = normalizeKnowledgeBody(content).replaceAll("(?U)\\s+", " ").trim();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", e);
        }
    }

    /** 与管理员知识去重使用同一规则规范化正文。 */
    public static String normalizeKnowledgeBody(String content) {
        if (content == null) {
            return "";
        }
        return content.replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // 极罕见；退化为内容本身（仍可用于幂等弱校验）
            return StringUtils.hasText(input) ? Integer.toHexString(input.hashCode()) : "empty";
        }
    }
}
