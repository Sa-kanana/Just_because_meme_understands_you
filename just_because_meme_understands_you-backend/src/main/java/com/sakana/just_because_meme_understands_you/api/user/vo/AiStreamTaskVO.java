package com.sakana.just_because_meme_understands_you.api.user.vo;

import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskSnapshot;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskStatus;

/** 返回给前端的后台 AI 搜索任务信息。 */
public record AiStreamTaskVO(
        /** 后台 AI 搜索任务的唯一标识。 */
        String taskId,
        /** 本次搜索所属的会话 ID。 */
        Long sessionId,
        /** 客户端用于重试创建请求的幂等 ID。 */
        String requestId,
        /** 任务当前生命周期状态。 */
        AiStreamTaskStatus status,
        /** 任务与事件回放数据的过期 Unix 毫秒时间戳。 */
        long expiresAtMillis) {

    /** 将内部任务快照映射为不包含用户 ID 的 API 视图。 */
    public static AiStreamTaskVO from(AiStreamTaskSnapshot snapshot) {
        return new AiStreamTaskVO(
                snapshot.taskId(),
                snapshot.sessionId(),
                snapshot.requestId(),
                snapshot.status(),
                snapshot.expiresAtMillis());
    }
}
