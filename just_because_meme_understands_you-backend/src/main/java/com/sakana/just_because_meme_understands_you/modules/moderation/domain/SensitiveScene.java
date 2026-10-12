package com.sakana.just_because_meme_understands_you.modules.moderation.domain;

/** 中文注释：标识敏感词检查所在业务，以便无审核队列的场景采用安全拒绝策略。 */
public enum SensitiveScene {
    /** 中文注释：梗名称，提交后进入梗审核队列。 */
    MEME_TITLE,
    /** 中文注释：梗介绍，提交后进入梗审核队列。 */
    MEME_DESCRIPTION,
    /** 中文注释：相关资源标题，随梗内容一并审核。 */
    MEME_RESOURCE_TITLE,
    /** 中文注释：评论目前没有待审状态，REVIEW 命中时拒绝提交。 */
    COMMENT,
    /** 中文注释：用户资料没有待审状态，REVIEW 命中时拒绝提交。 */
    USER_NICKNAME,
    /** 中文注释：用户签名没有待审状态，REVIEW 命中时拒绝提交。 */
    USER_SIGNATURE
}
