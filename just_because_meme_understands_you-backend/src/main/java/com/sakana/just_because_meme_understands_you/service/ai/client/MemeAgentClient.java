package com.sakana.just_because_meme_understands_you.service.ai.client;

import com.sakana.just_because_meme_understands_you.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.config.MemeAgentWebClientConfig;
import com.sakana.just_because_meme_understands_you.dto.MemeAgentIngestRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.MemeAgentStreamRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 内网调用 MemeAgent FastAPI。
 */
@Slf4j
@Component
public class MemeAgentClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private final WebClient memeAgentWebClient;
    private final MemeAgentProperties properties;

    public MemeAgentClient(@Qualifier(MemeAgentWebClientConfig.MEME_AGENT_WEB_CLIENT) WebClient memeAgentWebClient,
                           MemeAgentProperties properties) {
        this.memeAgentWebClient = memeAgentWebClient;
        this.properties = properties;
    }

    public Flux<String> stream(MemeAgentStreamRequestDTO request) {
        String path = properties.getAgent().getStreamPath();
        return memeAgentWebClient.post()
                .uri(path)
                .header(INTERNAL_API_KEY_HEADER, properties.getAgent().getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .doOnError(e -> log.error("MemeAgent stream failed requestId={}", request.getRequestId(), e));
    }

    public Mono<Void> ingestAsync(MemeAgentIngestRequestDTO request) {
        String path = properties.getAgent().getIngestPath();
        return memeAgentWebClient.post()
                .uri(path)
                .header(INTERNAL_API_KEY_HEADER, properties.getAgent().getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(e -> log.error("MemeAgent ingest failed", e));
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties.getAgent().getApiKey())
                && StringUtils.hasText(properties.getAgent().getBaseUrl());
    }
}
