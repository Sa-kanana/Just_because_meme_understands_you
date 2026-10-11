package com.sakana.just_because_meme_understands_you.modules.ai.domain;

/** 可写入 Redis 事件列表并通过 SSE 回放的事件。 */
public record AiStreamTaskEvent(
        /** 任务内单调递增的事件 ID，同时用作 SSE id。 */
        long id,
        /** 原有 SSE 事件名，例如 token、cite 或 done。 */
        String event,
        /** 原样保留的 SSE data 内容。 */
        String data) {
}
