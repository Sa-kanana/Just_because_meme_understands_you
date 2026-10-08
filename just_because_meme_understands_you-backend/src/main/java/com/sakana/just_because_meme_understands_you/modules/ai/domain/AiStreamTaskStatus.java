package com.sakana.just_because_meme_understands_you.modules.ai.domain;

/** AI 流式任务的生命周期状态。 */
public enum AiStreamTaskStatus {
    /** 任务仍在生成中。 */
    RUNNING,
    /** 已收到完整结束事件并完成持久化。 */
    COMPLETED,
    /** 上游生成失败。 */
    FAILED,
    /** 用户主动取消。 */
    CANCELLED,
    /** 任务超过有效期并被清理。 */
    EXPIRED
}
