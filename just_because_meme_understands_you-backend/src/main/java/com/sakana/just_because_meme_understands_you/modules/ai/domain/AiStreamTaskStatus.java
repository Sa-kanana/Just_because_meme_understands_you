package com.sakana.just_because_meme_understands_you.modules.ai.domain;

/** AI 流式任务的生命周期状态。 */
public enum AiStreamTaskStatus {
    /** 任务仍在生成中。 */
    GENERATING,
    /** 已收到完整结束事件并完成持久化。 */
    COMPLETED,
    /** 上游生成失败。 */
    FAILED,
    /** 用户主动取消。 */
    STOPPED,
    /** 任务超过有效期并被清理。 */
    EXPIRED,
    /** 兼容升级前 Redis 中仍在运行的任务状态。 */
    @Deprecated
    RUNNING,
    /** 兼容升级前 Redis 中已经取消的任务状态。 */
    @Deprecated
    CANCELLED;

    /** 将 Redis 中的旧状态名转换为当前对外状态名。 */
    public static AiStreamTaskStatus fromStoredValue(String value) {
        return switch (value) {
            case "RUNNING" -> GENERATING;
            case "CANCELLED" -> STOPPED;
            default -> valueOf(value);
        };
    }

    /** 判断新旧状态中表示仍在生成的值。 */
    public boolean isGenerating() {
        return this == GENERATING || this == RUNNING;
    }
}
