package com.sakana.just_because_meme_understands_you.service.ai.support;

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
