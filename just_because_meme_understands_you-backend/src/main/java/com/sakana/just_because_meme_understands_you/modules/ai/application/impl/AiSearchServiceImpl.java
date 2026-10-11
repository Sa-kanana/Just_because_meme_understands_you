package com.sakana.just_because_meme_understands_you.modules.ai.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.api.user.dto.AiSearchStreamRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentStreamRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AiChatMessage;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AiChatSession;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.AiChatMessageMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.AiChatSessionMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiSearchService;
import com.sakana.just_because_meme_understands_you.modules.ai.application.AiSearchPreparation;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.modules.ai.application.support.AiAnswerCache;
import com.sakana.just_because_meme_understands_you.modules.ai.application.support.AiRateLimiter;
import com.sakana.just_because_meme_understands_you.modules.ai.application.support.TokenBudgetTrimmer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

@Slf4j
@Service
public class AiSearchServiceImpl implements IAiSearchService {

    // 提示词或模型策略变化时同步升级，避免复用旧流水线生成的答案。
    private static final String AI_ANSWER_CACHE_PIPELINE_VERSION = "meme-search-v6-natural-language";
    private static final String COMPLETED_MESSAGE_STATUS = "COMPLETED";

    private final MemeAgentClient memeAgentClient;
    private final MemeAgentProperties properties;
    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final TokenBudgetTrimmer tokenBudgetTrimmer;
    private final AiAnswerCache aiAnswerCache;
    private final AiRateLimiter aiRateLimiter;
    private final ObjectMapper objectMapper;

    public AiSearchServiceImpl(MemeAgentClient memeAgentClient,
                               MemeAgentProperties properties,
                               AiChatSessionMapper sessionMapper,
                               AiChatMessageMapper messageMapper,
                               TokenBudgetTrimmer tokenBudgetTrimmer,
                               AiAnswerCache aiAnswerCache,
                               AiRateLimiter aiRateLimiter,
                               ObjectMapper objectMapper) {
        this.memeAgentClient = memeAgentClient;
        this.properties = properties;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.tokenBudgetTrimmer = tokenBudgetTrimmer;
        this.aiAnswerCache = aiAnswerCache;
        this.aiRateLimiter = aiRateLimiter;
        this.objectMapper = objectMapper;
    }


    @Override
    public AiSearchPreparation prepareSearch(Long userId, AiSearchStreamRequestDTO request) {
        if (!memeAgentClient.isConfigured()) {
            throw new BizException(Result.CODE_ERROR, "AI 搜索服务未配置");
        }
        if (!aiRateLimiter.tryAcquire(userId)) {
            throw new BizException(Result.CODE_TOO_MANY_REQUESTS, "AI 搜索过于频繁，请稍后再试");
        }
        //处理prompt
        String query = request.getQuery().trim();
        String requestId = StringUtils.hasText(request.getRequestId())
                ? request.getRequestId().trim()
                : UUID.randomUUID().toString().replace("-", "");
        //根据会话id和用户id获取会话信息
        AiChatSession session = resolveSession(userId, request.getSessionId(), query);
        Long sessionId = session.getId();

        //获取历史会话
        List<AiChatMessage> history = loadHistory(sessionId);
        //保存用户消息到数据库
        saveMessage(sessionId, "user", query, requestId);

        Flux<ServerSentEvent<String>> sessionEvent = Flux.just(sse(
                "session",
                Map.of("session_id", String.valueOf(sessionId), "request_id", requestId)));
        List<AiChatMessage> trimmed = tokenBudgetTrimmer.trim(history, properties.getAi().getMaxHistoryTokens());
        MemeAgentStreamRequestDTO agentRequest = buildAgentRequest(
                userId, sessionId, requestId, query, trimmed);
        Map<String, Object> answerContext = new LinkedHashMap<>();
        answerContext.put("pipeline_version", AI_ANSWER_CACHE_PIPELINE_VERSION);
        answerContext.put("messages", agentRequest.getMessages());
        answerContext.put("locale", agentRequest.getContext().getLocale());
        answerContext.put("hint_meme_ids", agentRequest.getContext().getHintMemeIds());
        answerContext.put("extra", agentRequest.getContext().getExtra());
        answerContext.put("max_tokens", agentRequest.getMaxTokens());
        String contextFingerprint = aiAnswerCache.fingerprint(answerContext);
        // 缓存键包含历史与业务候选的指纹，避免相同问题跨上下文复用旧答案。
        Optional<String> cached = aiAnswerCache.get(userId, query, contextFingerprint);
        //如果缓存命中
        if (cached.isPresent()) {
            String answer = cached.get();
            //返回拼接好的缓存答案流
            Flux<ServerSentEvent<String>> cachedEvents = Mono.fromRunnable(() -> {
                        saveAssistantMessage(sessionId, requestId, answer, "COMPLETED");
                        touchSession(sessionId);
                    })
                    // MyBatis/JDBC 写入使用有界弹性线程池，避免阻塞响应式或 Servlet 请求线程。
                    .subscribeOn(Schedulers.boundedElastic())
                    .thenMany(Flux.concat(sessionEvent, replayCachedAnswer(requestId, answer)));
            return new AiSearchPreparation(sessionId, requestId, cachedEvents);
        }
        //如果缓存未命中
        StringBuilder assistantBuffer = new StringBuilder();
        AtomicBoolean doneReceived = new AtomicBoolean(false);
        AtomicBoolean errorReceived = new AtomicBoolean(false);
        AtomicBoolean vectorEvidenceReceived = new AtomicBoolean(false);
        AtomicReference<String> finishReason = new AtomicReference<>("");
        long idleTimeoutMs = Math.max(1L, properties.getAi().getStreamIdleTimeoutMs());

        Flux<ServerSentEvent<String>> agentEvents = memeAgentClient.stream(agentRequest)
                        .timeout(Duration.ofMillis(idleTimeoutMs))
                        .doOnNext(event -> observeCompletionEvent(
                                event, doneReceived, errorReceived, finishReason))
                        .map(this::sanitizeErrorEvent)
                        .doOnNext(event -> {
                            if (event != null && "cite".equals(event.event())) {
                                vectorEvidenceReceived.set(true);
                            }
                            if (!doneReceived.get() && !errorReceived.get()) {
                                accumulateAssistant(event, assistantBuffer);
                            }
                        })
                        .doOnError(error -> log.error(
                                "AI stream error userId={} sessionId={} errorType={}",
                                userId,
                                sessionId,
                                error.getClass().getSimpleName()))
                        .onErrorResume(e -> {
                            errorReceived.set(true);
                            return Flux.just(formatErrorEvent());
                        });

        Flux<ServerSentEvent<String>> events = Flux.concat(sessionEvent, agentEvents)
                .concatWith(Mono.fromRunnable(() -> {
                            String answer = assistantBuffer.toString();
                            if (doneReceived.get()
                                    && !errorReceived.get()
                                    && StringUtils.hasText(answer)) {
                                saveAssistantMessage(sessionId, requestId, answer, "COMPLETED");
                                if (!finishReason.get().startsWith("fallback")
                                        && vectorEvidenceReceived.get()) {
                                    aiAnswerCache.put(userId, query, contextFingerprint, answer);
                                }
                            }
                            touchSession(sessionId);
                        })
                        // 流完成后的 MySQL/Redis 操作不占用 Reactor Netty 事件循环。
                        .subscribeOn(Schedulers.boundedElastic())
                        .thenMany(Flux.<ServerSentEvent<String>>empty()));
        return new AiSearchPreparation(sessionId, requestId, events);
    }


    /**
    * 将缓冲中的缓存答案变为流式输出
    */
    private Flux<ServerSentEvent<String>> replayCachedAnswer(String requestId, String answer) {
        List<ServerSentEvent<String>> events = new ArrayList<>();
        // ① 发送元数据事件（标记为缓存来源）
        events.add(sse("meta", Map.of("request_id", requestId, "retrieved", List.of())));
        // ② 将完整答案切片，模拟流式输出
        int step = 16;// 每次发送 32 个字符
        for (int i = 0; i < answer.length(); i += step) {
            String piece = answer.substring(i, Math.min(i + step, answer.length()));
            events.add(sse("token", Map.of("text", piece)));
        }
        // ③ 发送结束事件（标记来源为 cache
        events.add(sse("done", Map.of("finish_reason", "cache", "usage", Map.of())));
        return Flux.fromIterable(events);
    }

    /**
     * 累积处理 AI 回答事件，将文本追加到缓冲区,以便后续保存和缓存
     * @param event
     * @param buffer
     */
    private void accumulateAssistant(ServerSentEvent<String> event, StringBuilder buffer) {
        // ① 过滤空数据
        if (event == null || !StringUtils.hasText(event.data())) {
            return;
        }
        // ② 过滤非文本事件
        String eventName = event.event() != null ? event.event() : "";
        // WebClient 解码后通常只有 data；token 事件名可能为空，按 JSON 字段兼容
        // 只处理 "token" 或 "message" 类型的事件，忽略 meta/done/cite 等
        if (StringUtils.hasText(eventName) && !"token".equals(eventName) && !"message".equals(eventName)) {
            return;
        }
        // ③ 解析 JSON 并提取文本
        try {
            JsonNode node = objectMapper.readTree(event.data());
            if (node.has("text")) {
                // 将提取到的文本追加到缓冲区
                buffer.append(node.get("text").asText(""));
            }
        } catch (Exception e) {
            log.debug("Skip non-json sse data", e);
        }
    }

    /**
     * 格式化错误事件，包含错误码和消息
     * @param error
     * @return
     */
    private void observeCompletionEvent(ServerSentEvent<String> event,
                                        AtomicBoolean doneReceived,
                                        AtomicBoolean errorReceived,
                                        AtomicReference<String> finishReason) {
        if (event == null || !StringUtils.hasText(event.event())) {
            return;
        }
        if ("error".equals(event.event())) {
            errorReceived.set(true);
            return;
        }
        if (!"done".equals(event.event())) {
            return;
        }
        doneReceived.set(true);
        try {
            JsonNode payload = objectMapper.readTree(event.data());
            finishReason.set(payload.path("finish_reason").asText(""));
        } catch (Exception e) {
            log.debug("Could not read AI stream finish reason");
        }
    }

    private ServerSentEvent<String> sanitizeErrorEvent(ServerSentEvent<String> event) {
        if (event == null || !"error".equals(event.event())) {
            return event;
        }
        return sse("error", Map.of(
                "code", "upstream_error",
                "message", "AI 搜索服务暂时不可用，请稍后重试。"));
    }

    private ServerSentEvent<String> formatErrorEvent() {
        return sse("error", Map.of(
                "code", "upstream_error",
                "message", "AI 搜索服务暂时不可用，请稍后重试。"));
    }

    /**
     * 构建 SSE 事件，包含事件名和数据
     * @param event
     * @param payload
     * @return
     */
    private ServerSentEvent<String> sse(String event, Object payload) {
        try {
            return ServerSentEvent.<String>builder()
                    .event(event)
                    .data(objectMapper.writeValueAsString(payload))
                    .build();
        } catch (Exception e) {
            return ServerSentEvent.<String>builder()
                    .event(event)
                    .data("{\"message\":\"sse_encode_error\"}")
                    .build();
        }
    }

    /**
     * 构建 MemeAgent 流式输出请求参数
     * @param userId
     * @param sessionId
     * @param requestId
     * @param query
     * @param trimmed
     * @return
     */
    private MemeAgentStreamRequestDTO buildAgentRequest(Long userId,
                                                        Long sessionId,
                                                        String requestId,
                                                        String query,
                                                        List<AiChatMessage> trimmed) {
        List<MemeAgentStreamRequestDTO.ChatMessage> messages = new ArrayList<>();
        for (AiChatMessage msg : trimmed) {
            if (msg == null || !StringUtils.hasText(msg.getRole()) || !StringUtils.hasText(msg.getContent())) {
                continue;
            }
            String role = msg.getRole().trim().toLowerCase(Locale.ROOT);
            // 仅转发 user/assistant，禁止把 system 历史交给 Agent（防提示注入提权）
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            messages.add(MemeAgentStreamRequestDTO.ChatMessage.builder()
                    .role(role)
                    .content(msg.getContent().trim())
                    .build());
        }
        //构造DTO
        return MemeAgentStreamRequestDTO.builder()
                .requestId(requestId)
                .sessionId(String.valueOf(sessionId))
                .query(query)
                .messages(messages)
                .context(MemeAgentStreamRequestDTO.BusinessContext.builder()
                        .userId(String.valueOf(userId))
                        .locale("zh-CN")
                        // 梗候选与相关度只由 MemeAgent 向量检索确定。
                        .hintMemeIds(List.of())
                        .extra(Map.of())
                        .build())
                .maxTokens(Math.min(
                        Math.max(64, properties.getAi().getMaxOutputTokens()),
                        2048))
                .build();
    }

    /**
     * 解析会话 ID，根据前端传的 sessionId 选择复用旧会话或创建新会话
     * @param userId
     * @param sessionIdRaw
     * @param query
     * @return
     */
    private AiChatSession resolveSession(Long userId, String sessionIdRaw, String query) {
        // ① 分支 A：如果前端传了 sessionId，尝试复用旧会话
        if (StringUtils.hasText(sessionIdRaw)) {
            Long sessionId = RequestIdParser.parseLongId(sessionIdRaw, "sessionId");
            // 查询数据库：必须同时满足 ID 匹配、用户归属匹配、未删除
            AiChatSession existing = sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                    .eq(AiChatSession::getId, sessionId)
                    .eq(AiChatSession::getUserId, userId)
                    .eq(AiChatSession::getIsDeleted, 0));
            if (existing != null) {
                return existing;    // 找到则返回
            }
            throw new BizException(Result.CODE_NOT_FOUND, "会话不存在或已删除");
        }
        // ② 分支 B：前端没传 sessionId，创建新会话
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setTitle(truncateTitle(query));// 用用户的第一句话作为会话标题
        session.setIsDeleted(0);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);// 插入数据库，生成主键 ID
        return session;
    }

    /**
     * 更新会话时间
     * @param sessionId
     */
    private void touchSession(Long sessionId) {
        AiChatSession patch = new AiChatSession();
        patch.setId(sessionId);
        patch.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(patch);
    }

    /**
     * 加载会话历史消息
     * @param sessionId
     * @return
     */
    private List<AiChatMessage> loadHistory(Long sessionId) {
        List<AiChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreateTime));
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        // 停止或失败的回答继续保留在会话历史供前端展示，但不作为模型上下文。
        return messages.stream()
                .filter(Objects::nonNull)
                .filter(message -> COMPLETED_MESSAGE_STATUS.equalsIgnoreCase(message.getStatus()))
                .toList();
    }

    /**
     * 保存会话消息
     * @param sessionId
     * @param role
     * @param content
     * @param requestId
     */
    private void saveMessage(Long sessionId, String role, String content, String requestId) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setStatus("COMPLETED");
        message.setContent(content);
        message.setRequestId(requestId);
        message.setTokenEstimate(tokenBudgetTrimmer.estimateTokens(content));
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
    }

    @Override
    public void persistStoppedAnswer(Long sessionId, String requestId, String content) {
        if (sessionId == null || !StringUtils.hasText(requestId) || !StringUtils.hasText(content)) {
            return;
        }
        saveAssistantMessage(sessionId, requestId, content, "STOPPED");
        touchSession(sessionId);
    }

    private void saveAssistantMessage(Long sessionId, String requestId, String content, String status) {
        List<AiChatMessage> existingMessages = messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .eq(AiChatMessage::getRole, "assistant")
                .eq(AiChatMessage::getRequestId, requestId)
                .orderByDesc(AiChatMessage::getCreateTime)
                .last("LIMIT 1"));
        if (existingMessages != null && !existingMessages.isEmpty()) {
            AiChatMessage existingMessage = existingMessages.get(0);
            existingMessage.setContent(content);
            existingMessage.setStatus(status);
            existingMessage.setTokenEstimate(tokenBudgetTrimmer.estimateTokens(content));
            messageMapper.updateById(existingMessage);
            return;
        }

        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setRole("assistant");
        message.setStatus(status);
        message.setContent(content);
        message.setRequestId(requestId);
        message.setTokenEstimate(tokenBudgetTrimmer.estimateTokens(content));
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
    }

    /**
     * 截断会话标题，确保不超过 32 个字符
     * @param query
     * @return
     */
    private static String truncateTitle(String query) {
        if (!StringUtils.hasText(query)) {
            return "新对话";
        }
        String trimmed = query.trim();
        return trimmed.length() <= 32 ? trimmed : trimmed.substring(0, 32) + "…";
    }
}
