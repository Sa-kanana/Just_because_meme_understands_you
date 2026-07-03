package com.sakana.just_because_meme_understands_you.filter;

import lombok.Getter;

/**
 * 敏感词过滤结果。
 */
@Getter
public class SensitiveFilterResult {

    private final String content;
    private final boolean rejected;
    private final boolean needAudit;

    private SensitiveFilterResult(String content, boolean rejected, boolean needAudit) {
        this.content = content;
        this.rejected = rejected;
        this.needAudit = needAudit;
    }

    public static SensitiveFilterResult pass(String content) {
        return new SensitiveFilterResult(content, false, false);
    }

    public static SensitiveFilterResult replaced(String content) {
        return new SensitiveFilterResult(content, false, false);
    }

    public static SensitiveFilterResult audit(String content) {
        return new SensitiveFilterResult(content, false, true);
    }

    public static SensitiveFilterResult reject(String content) {
        return new SensitiveFilterResult(content, true, false);
    }
}
