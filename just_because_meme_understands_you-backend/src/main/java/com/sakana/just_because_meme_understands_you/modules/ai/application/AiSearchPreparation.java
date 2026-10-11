package com.sakana.just_because_meme_understands_you.modules.ai.application;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 已完成同步校验与会话准备、等待独立订阅的 AI 搜索。
 */
public record AiSearchPreparation(
        /** 本次搜索所属的会话 ID。 */
        Long sessionId,
        /** 本次搜索使用的幂等请求 ID。 */
        String requestId,
        /** 惰性读取并生成 AI 搜索事件的上游流。 */
        Flux<ServerSentEvent<String>> events) {
}
