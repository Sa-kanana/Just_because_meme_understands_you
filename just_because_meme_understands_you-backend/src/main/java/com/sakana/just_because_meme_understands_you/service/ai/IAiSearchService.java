package com.sakana.just_because_meme_understands_you.service.ai;

import com.sakana.just_because_meme_understands_you.dto.AiSearchStreamRequestDTO;
import reactor.core.publisher.Flux;

public interface IAiSearchService {

    /**
     * AI 搜索流式输出（SSE 事件块，透传 MemeAgent）。
     */
    Flux<String> streamSearch(Long userId, AiSearchStreamRequestDTO request);
}
