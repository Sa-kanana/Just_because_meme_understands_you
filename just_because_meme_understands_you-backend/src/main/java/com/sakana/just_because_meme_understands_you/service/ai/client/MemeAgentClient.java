package com.sakana.just_because_meme_understands_you.service.ai.client;

import com.sakana.just_because_meme_understands_you.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.config.MemeAgentWebClientConfig;
import com.sakana.just_because_meme_understands_you.dto.MemeAgentCrawlRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.MemeAgentCrawlResponseDTO;
import com.sakana.just_because_meme_understands_you.dto.MemeAgentIngestRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.MemeAgentKnowledgeIngestRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.MemeAgentStreamRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
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

    private static final ParameterizedTypeReference<ServerSentEvent<String>> SSE_TYPE =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient memeAgentWebClient;
    private final MemeAgentProperties properties;

    public MemeAgentClient(@Qualifier(MemeAgentWebClientConfig.MEME_AGENT_WEB_CLIENT) WebClient memeAgentWebClient,
                           MemeAgentProperties properties) {
        this.memeAgentWebClient = memeAgentWebClient;
        this.properties = properties;
    }

    private String resolveWriteApiKey() {
        String write = properties.getAgent().getWriteApiKey();
        if (StringUtils.hasText(write)) {
            return write;
        }
        return properties.getAgent().getApiKey();
    }

    /**
     * 以 ServerSentEvent 解码上游 SSE，保留 event 名（token/meta/cite/done/error）。
     */
    public Flux<ServerSentEvent<String>> stream(MemeAgentStreamRequestDTO request) {
        String path = properties.getAgent().getStreamPath();// ① 获取路径（默认 /stream）
        return memeAgentWebClient.post()
                .uri(path)
                // ② 设置请求头
                .header(INTERNAL_API_KEY_HEADER, properties.getAgent().getApiKey())// 鉴权
                .contentType(MediaType.APPLICATION_JSON)// 请求体格式
                .accept(MediaType.TEXT_EVENT_STREAM)// 声明接收 SSE 格式
                .bodyValue(request)// 发送请求体
                .retrieve()// ③ 发起请求
                // ④ 特殊处理 422 错误（参数校验失败）
                .onStatus(status -> status.value() == 422, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> {
                                    log.error("MemeAgent /stream 422 requestId={} body={}",
                                            request.getRequestId(), body);
                                    return response.createException();
                                }))
                // ⑤ 将响应体解析为 SSE 事件流
                .bodyToFlux(SSE_TYPE)
                .map(this::normalizeSse)
                // ⑦ 错误日志记录
                .doOnError(e -> log.error("MemeAgent stream failed requestId={}", request.getRequestId(), e));
    }

    public Mono<Void> ingestAsync(MemeAgentIngestRequestDTO request) {
        String path = properties.getAgent().getIngestPath();// ① 获取路径（默认 /ingest）
        return memeAgentWebClient.post()
                .uri(path)
                .header(INTERNAL_API_KEY_HEADER, resolveWriteApiKey())// 使用写密钥（权限更高）
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)// 发送灌库数据
                .retrieve()// ③ 发起请求
                .toBodilessEntity()
                .then()// ⑤ 转换为 Mono<Void>

                .doOnError(e -> log.error("MemeAgent ingest failed", e));// ⑥ 错误日志
    }

    public Mono<Void> ingestKnowledgeAsync(MemeAgentKnowledgeIngestRequestDTO request) {
        String path = properties.getAgent().getKnowledgeIngestPath();
        return memeAgentWebClient.post()
                .uri(path)
                .header(INTERNAL_API_KEY_HEADER, resolveWriteApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(e -> log.error("MemeAgent knowledge ingest failed", e));
    }

    /**
     * 触发 Firecrawl 热梗采集，返回结构化候选。
     */
    public Mono<MemeAgentCrawlResponseDTO> crawlHotMemes(MemeAgentCrawlRequestDTO request) {
        String path = properties.getAgent().getCrawlPath();
        return memeAgentWebClient.post()
                .uri(path)
                .header(INTERNAL_API_KEY_HEADER, resolveWriteApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MemeAgentCrawlResponseDTO.class)
                .doOnError(e -> log.error("MemeAgent crawl failed", e));
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties.getAgent().getApiKey())
                && StringUtils.hasText(properties.getAgent().getBaseUrl());
    }

    /**
     * 探测 Agent {@code GET /health}；未配置或失败时返回空 Map（不抛业务异常）。
     */
    public Mono<java.util.Map<String, Object>> probeHealth() {
        if (!isConfigured()) {
            return Mono.just(java.util.Map.of());// 未配置 → 直接返回空，不发起请求
        }
        return memeAgentWebClient.get()
                .uri("/health")
                .retrieve()
                // 解析响应体为 Map
                .bodyToMono(new ParameterizedTypeReference<java.util.Map<String, Object>>() {
                })
                .timeout(java.time.Duration.ofMillis(
                        Math.max(500L, properties.getAgent().getConnectTimeoutMs())))
                .onErrorResume(e -> {
                    log.warn("MemeAgent /health 探测失败: {}", e.toString());
                    return Mono.just(java.util.Map.of());
                });
    }

    private ServerSentEvent<String> normalizeSse(ServerSentEvent<String> sse) {
        String event = StringUtils.hasText(sse.event()) ? sse.event() : "message";
        String data = sse.data() != null ? sse.data() : "";
        return ServerSentEvent.<String>builder()
                .event(event)
                .data(data)
                .id(sse.id())
                .comment(sse.comment())
                .build();
    }
}
