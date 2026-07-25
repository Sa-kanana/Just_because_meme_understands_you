package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.AiSearchStreamRequestDTO;
import com.sakana.just_because_meme_understands_you.service.ai.IAiSearchService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiSearchController {

    @Resource
    private IAiSearchService aiSearchService;

    /**
     * AI 搜索流式接口（SSE）。
     * POST /ai/search/stream
     */
    @PostMapping(value = "/search/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamSearch(@Valid @RequestBody AiSearchStreamRequestDTO request,
                                                      HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        log.info("AI search stream userId={} sessionId={}", userId, request.getSessionId());
        return aiSearchService.streamSearch(userId, request);
    }
}
