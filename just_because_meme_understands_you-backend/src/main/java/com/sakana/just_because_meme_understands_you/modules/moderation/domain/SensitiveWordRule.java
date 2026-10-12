package com.sakana.just_because_meme_understands_you.modules.moderation.domain;

/** 中文注释：供内存匹配器使用的不可变敏感词规则。 */
public record SensitiveWordRule(String word, String category, SensitiveLevel level, String replacement) {
}
