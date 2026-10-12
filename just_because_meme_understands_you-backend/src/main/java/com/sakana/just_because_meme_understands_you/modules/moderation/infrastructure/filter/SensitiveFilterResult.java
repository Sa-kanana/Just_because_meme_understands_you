package com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.filter;

import lombok.Getter;

import java.util.List;

/** 中文注释：敏感词检查的结构化结果，包含处置决策、过滤文本和命中明细。 */
@Getter
public class SensitiveFilterResult {

    /** 中文注释：统一的审核决策枚举。 */
    public enum Decision {
        PASS,
        REVIEW,
        BLOCK
    }

    private final Decision decision;
    private final String content;
    private final List<SensitiveWordMatch> matches;
    private final boolean rejected;
    private final boolean needAudit;

    private SensitiveFilterResult(Decision decision, String content, List<SensitiveWordMatch> matches) {
        this.decision = decision;
        this.content = content;
        this.matches = matches == null ? List.of() : List.copyOf(matches);
        this.rejected = decision == Decision.BLOCK;
        this.needAudit = decision == Decision.REVIEW;
    }

    public static SensitiveFilterResult pass(String content) {
        return new SensitiveFilterResult(Decision.PASS, content, List.of());
    }

    public static SensitiveFilterResult of(Decision decision, String content, List<SensitiveWordMatch> matches) {
        return new SensitiveFilterResult(decision, content, matches);
    }
}
