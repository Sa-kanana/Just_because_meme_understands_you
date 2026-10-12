package com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentWebClientConfig;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentCrawlRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentCrawlResponseDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentIngestRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentKnowledgeIngestRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentKnowledgeIngestErrorDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentMemeIdPageDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentStreamRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
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
    private final ObjectMapper objectMapper;

    public MemeAgentClient(@Qualifier(MemeAgentWebClientConfig.MEME_AGENT_WEB_CLIENT) WebClient memeAgentWebClient,
                           MemeAgentProperties properties,
                           ObjectMapper objectMapper) {
        this.memeAgentWebClient = memeAgentWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
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
                                    log.error("MemeAgent /stream 422 requestId={} bodyChars={}",
                                            request.getRequestId(), body.length());
                                    return response.createException();
                                }))
                // ⑤ 将响应体解析为 SSE 事件流
                .bodyToFlux(SSE_TYPE)
                .map(this::normalizeSse)
                .doOnError(e -> log.error(
                        "MemeAgent stream failed requestId={} errorType={}",
                        request.getRequestId(),
                        e.getClass().getSimpleName()));
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

    /** 分页读取 MemeAgent 向量库的梗 ID；使用写密钥访问内部校准接口。 */
    public Mono<MemeAgentMemeIdPageDTO> listMemeIdsPage(String cursor, int limit) {
        String path = properties.getAgent().getIngestPath().replaceAll("/+$", "") + "/meme-ids";
        return memeAgentWebClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(path).queryParam("limit", limit);
                    if (StringUtils.hasText(cursor)) {
                        builder.queryParam("cursor", cursor);
                    }
                    return builder.build();
                })
                .header(INTERNAL_API_KEY_HEADER, resolveWriteApiKey())
                .retrieve()
                .bodyToMono(MemeAgentMemeIdPageDTO.class)
                .doOnError(error -> log.error(
                        "MemeAgent meme ID page failed limit={} errorType={}",
                        limit,
                        error.getClass().getSimpleName()));
    }

    public Mono<Void> ingestKnowledgeAsync(MemeAgentKnowledgeIngestRequestDTO request) {
        String path = properties.getAgent().getKnowledgeIngestPath();
        return memeAgentWebClient.post()
                .uri(path)
                .header(INTERNAL_API_KEY_HEADER, resolveWriteApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        // 中文注释：只解析白名单字段并构造安全异常，不将上游原始响应写入用户错误或日志。
                        .map(this::toSafeKnowledgeIngestException))
                .toBodilessEntity()
                .then()
                .doOnError(e -> log.error("MemeAgent knowledge ingest failed", e));
    }

    private RuntimeException toSafeKnowledgeIngestException(String responseBody) {
        try {
            MemeAgentKnowledgeIngestErrorDTO response = objectMapper.readValue(
                    responseBody, MemeAgentKnowledgeIngestErrorDTO.class);
            MemeAgentKnowledgeIngestErrorDTO.Detail detail = response.getDetail();
            if (detail == null || !"KNOWLEDGE_EMBEDDING_FAILED".equals(detail.getCode())
                    || detail.getCompletedChunks() == null || detail.getTotalChunks() == null
                    || detail.getCompletedChunks() < 0 || detail.getTotalChunks() < 0
                    || detail.getCompletedChunks() > detail.getTotalChunks()) {
                return new IllegalStateException("知识向量处理失败，服务端错误详情无效");
            }

            String stage = knowledgeFailureStage(detail.getStage());
            String message = safeMessage(detail.getMessage());
            String progress = detail.getTotalChunks() == 0
                    ? ""
                    : "已保存 " + detail.getCompletedChunks() + "/" + detail.getTotalChunks() + " 个片段。";
            return new IllegalStateException(progress + "知识向量索引处理失败（" + stage
                    + "）。" + (message.isBlank() ? "" : " " + message));
        } catch (Exception ignored) {
            // 中文注释：解析失败时不传播响应正文，避免错误页或 index_error 暴露上游原始内容。
            return new IllegalStateException("知识向量处理失败，服务端错误详情无法解析");
        }
    }

    private static String knowledgeFailureStage(String stage) {
        if (stage == null) {
            return "向量处理";
        }
        return switch (stage) {
            case "embedding" -> "向量生成";
            case "validation" -> "向量校验";
            case "checkpoint_load" -> "读取进度";
            case "checkpoint_persist" -> "保存进度";
            case "checkpoint_validation" -> "检查点校验";
            case "publish" -> "发布向量";
            default -> "向量处理";
        };
    }

    private static String safeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        StringBuilder safe = new StringBuilder();
        for (int index = 0; index < message.length() && safe.length() < 240; index++) {
            char value = message.charAt(index);
            safe.append(Character.isISOControl(value) ? ' ' : value);
        }
        return safe.toString().trim();
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
