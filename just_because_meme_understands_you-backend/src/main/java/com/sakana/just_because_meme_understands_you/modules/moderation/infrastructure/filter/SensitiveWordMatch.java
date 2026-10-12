package com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.filter;

import com.sakana.just_because_meme_understands_you.modules.moderation.domain.SensitiveLevel;

/** 中文注释：一次敏感词命中，索引对应原始文本的 UTF-16 闭区间。 */
public record SensitiveWordMatch(String word, String category, SensitiveLevel level, int start, int end) {
}
