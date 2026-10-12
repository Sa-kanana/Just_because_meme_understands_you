package com.sakana.just_because_meme_understands_you.modules.meme.domain;

import java.util.Arrays;

/** 中文注释：标签分类及其用于接口展示的中文名称和固定排序。 */
public enum MemeTagCategory {
    SOURCE("SOURCE", "来源", 10),
    FORMAT("FORMAT", "形式", 20),
    EMOTION("EMOTION", "情绪", 30),
    SCENARIO("SCENARIO", "场景", 40),
    CONTENT("CONTENT", "内容", 50),
    ERA("ERA", "时代", 60),
    PROPAGATION("PROPAGATION", "传播特征", 70),
    TONE("TONE", "使用语气", 80),
    UNCATEGORIZED("UNCATEGORIZED", "未分类", 90);

    private final String code;
    private final String label;
    private final int order;

    MemeTagCategory(String code, String label, int order) {
        this.code = code;
        this.label = label;
        this.order = order;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public int getOrder() {
        return order;
    }

    /** 中文注释：将数据库分类代码映射为中文分类；未知旧值安全归入未分类。 */
    public static MemeTagCategory fromCode(String code) {
        if (code == null) {
            return UNCATEGORIZED;
        }
        return Arrays.stream(values())
                .filter(category -> category.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(UNCATEGORIZED);
    }

    /** 中文注释：判断管理端提交的分类代码是否为当前支持的分类。 */
    public static boolean supports(String code) {
        return code != null && Arrays.stream(values())
                .anyMatch(category -> category != UNCATEGORIZED && category.code.equalsIgnoreCase(code.trim()));
    }
}
