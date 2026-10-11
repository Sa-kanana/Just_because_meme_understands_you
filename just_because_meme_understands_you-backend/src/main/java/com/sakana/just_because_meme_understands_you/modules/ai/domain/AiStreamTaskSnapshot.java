package com.sakana.just_because_meme_understands_you.modules.ai.domain;

/** Redis 中可供 API 和事件订阅使用的任务状态快照。 */
public record AiStreamTaskSnapshot(
        /** AI 流式任务的唯一标识。 */
        String taskId,
        /** 创建任务的登录用户 ID。 */
        Long userId,
        /** 本次搜索所属的会话 ID，准备阶段完成后写入。 */
        Long sessionId,
        /** 客户端提供或服务端生成的幂等请求 ID。 */
        String requestId,
        /** 任务当前生命周期状态。 */
        AiStreamTaskStatus status,
        /** 任务创建时间的 Unix 毫秒时间戳。 */
        long createdAtMillis,
        /** 任务和事件允许保留到的 Unix 毫秒时间戳。 */
        long expiresAtMillis,
        /** 最近一个已存储事件的单调递增 ID。 */
        long lastEventId) {
}
