package com.sakana.just_because_meme_understands_you.modules.moderation.domain;

/** 中文注释：敏感词命中的风险等级，分类描述词语类型，等级决定业务处置方式。 */
public enum SensitiveLevel {
    /** 中文注释：允许提交；配置 replacement 时替换命中词。 */
    NORMAL(1),
    /** 中文注释：进入现有人工审核流程；没有审核队列的业务会拒绝本次提交。 */
    REVIEW(2),
    /** 中文注释：直接阻止提交。 */
    BLOCK(3);

    private final int priority;

    SensitiveLevel(int priority) {
        this.priority = priority;
    }

    /** 中文注释：返回用于重叠命中决策的风险优先级。 */
    public int priority() {
        return priority;
    }

    /** 中文注释：从数据库值解析等级，未知值按直接拒绝处理以保证失败关闭。 */
    public static SensitiveLevel fromValue(String value) {
        if (value == null) {
            return BLOCK;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BLOCK;
        }
    }
}
