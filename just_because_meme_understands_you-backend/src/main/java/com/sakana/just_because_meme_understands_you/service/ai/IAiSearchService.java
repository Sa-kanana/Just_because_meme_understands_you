package com.sakana.just_because_meme_understands_you.service.ai;

import com.sakana.just_because_meme_understands_you.dto.AiSearchStreamRequestDTO;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface IAiSearchService {

    /**
     * AI 搜索流式输出（SSE，保留 event 名透传/规范化）。
     */
    Flux<ServerSentEvent<String>> streamSearch(Long userId, AiSearchStreamRequestDTO request);
}
