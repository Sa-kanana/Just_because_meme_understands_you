package com.sakana.just_because_meme_understands_you.service.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.dto.AiSearchStreamRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.MemeAgentStreamRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.AiChatMessage;
import com.sakana.just_because_meme_understands_you.entity.AiChatSession;
import com.sakana.just_because_meme_understands_you.mapper.AiChatMessageMapper;
import com.sakana.just_because_meme_understands_you.mapper.AiChatSessionMapper;
import com.sakana.just_because_meme_understands_you.service.ai.IAiSearchService;
import com.sakana.just_because_meme_understands_you.service.ai.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.service.ai.support.AiAnswerCache;
import com.sakana.just_because_meme_understands_you.service.ai.support.AiBusinessContextAssembler;
import com.sakana.just_because_meme_understands_you.service.ai.support.AiRateLimiter;
import com.sakana.just_because_meme_understands_you.service.ai.support.TokenBudgetTrimmer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class AiSearchServiceImpl implements IAiSearchService {

    private final MemeAgentClient memeAgentClient;
    private final MemeAgentProperties properties;
    private final AiChatSessionMapper sessionMapper;
    private final AiChatMessageMapper messageMapper;
    private final TokenBudgetTrimmer tokenBudgetTrimmer;
    private final AiAnswerCache aiAnswerCache;
    private final AiRateLimiter aiRateLimiter;
    private final AiBusinessContextAssembler businessContextAssembler;
    private final ObjectMapper objectMapper;

    public AiSearchServiceImpl(MemeAgentClient memeAgentClient,
                               MemeAgentProperties properties,
                               AiChatSessionMapper sessionMapper,
                               AiChatMessageMapper messageMapper,
                               TokenBudgetTrimmer tokenBudgetTrimmer,
                               AiAnswerCache aiAnswerCache,
                               AiRateLimiter aiRateLimiter,
                               AiBusinessContextAssembler businessContextAssembler,
                               ObjectMapper objectMapper) {
        this.memeAgentClient = memeAgentClient;
        this.properties = properties;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.tokenBudgetTrimmer = tokenBudgetTrimmer;
        this.aiAnswerCache = aiAnswerCache;
        this.aiRateLimiter = aiRateLimiter;
        this.businessContextAssembler = businessContextAssembler;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<ServerSentEvent<String>> streamSearch(Long userId, AiSearchStreamRequestDTO request) {
        if (!memeAgentClient.isConfigured()) {
            throw new BizException(Result.CODE_ERROR, "AI 搜索服务未配置");
        }
        if (!aiRateLimiter.tryAcquire(userId)) {
            throw new BizException(Result.CODE_TOO_MANY_REQUESTS, "AI 搜索过于频繁，请稍后再试");
        }

        String query = request.getQuery().trim();
        String requestId = StringUtils.hasText(request.getRequestId())
                ? request.getRequestId().trim()
                : UUID.randomUUID().toString().replace("-", "");

        AiChatSession session = resolveSession(userId, request.getSessionId(), query);
        Long sessionId = session.getId();

        List<AiChatMessage> history = loadHistory(sessionId);
        saveMessage(sessionId, "user", query, requestId);

        Flux<ServerSentEvent<String>> sessionEvent = Flux.just(sse(
                "session",
                Map.of("session_id", String.valueOf(sessionId), "request_id", requestId)));

        Optional<String> cached = aiAnswerCache.get(userId, query);
        if (cached.isPresent()) {
            String answer = cached.get();
            saveMessage(sessionId, "assistant", answer, requestId);
            touchSession(sessionId);
            return Flux.concat(sessionEvent, replayCachedAnswer(requestId, answer));
        }

        List<AiChatMessage> trimmed = tokenBudgetTrimmer.trim(history, properties.getAi().getMaxHistoryTokens());
        MemeAgentStreamRequestDTO agentRequest = buildAgentRequest(
                userId, sessionId, requestId, query, trimmed);

        AtomicReference<StringBuilder> assistantBuffer = new AtomicReference<>(new StringBuilder());

        return Flux.concat(
                sessionEvent,
                memeAgentClient.stream(agentRequest)
                        .doOnNext(event -> accumulateAssistant(event, assistantBuffer))
                        .onErrorResume(e -> {
                            log.error("AI stream error userId={} sessionId={}", userId, sessionId, e);
                            return Flux.just(formatErrorEvent(e));
                        })
                        .doOnComplete(() -> {
                            String answer = assistantBuffer.get().toString();
                            if (StringUtils.hasText(answer)) {
                                saveMessage(sessionId, "assistant", answer, requestId);
                                aiAnswerCache.put(userId, query, answer);
                            }
                            touchSession(sessionId);
                        }));
    }

    private Flux<ServerSentEvent<String>> replayCachedAnswer(String requestId, String answer) {
        List<ServerSentEvent<String>> events = new ArrayList<>();
        events.add(sse("meta", Map.of("request_id", requestId, "retrieved", List.of())));
        int step = 32;
        for (int i = 0; i < answer.length(); i += step) {
            String piece = answer.substring(i, Math.min(i + step, answer.length()));
            events.add(sse("token", Map.of("text", piece)));
        }
        events.add(sse("done", Map.of("finish_reason", "cache", "usage", Map.of())));
        return Flux.fromIterable(events);
    }

    private void accumulateAssistant(ServerSentEvent<String> event,
                                     AtomicReference<StringBuilder> buffer) {
        if (event == null || !StringUtils.hasText(event.data())) {
            return;
        }
        String eventName = event.event() != null ? event.event() : "";
        // WebClient 解码后通常只有 data；token 事件名可能为空，按 JSON 字段兼容
        if (StringUtils.hasText(eventName) && !"token".equals(eventName) && !"message".equals(eventName)) {
            return;
        }
        try {
            JsonNode node = objectMapper.readTree(event.data());
            if (node.has("text")) {
                buffer.get().append(node.get("text").asText(""));
            }
        } catch (Exception e) {
            log.debug("Skip non-json sse data", e);
        }
    }

    private ServerSentEvent<String> formatErrorEvent(Throwable error) {
        String message = error != null && StringUtils.hasText(error.getMessage())
                ? error.getMessage()
                : "AI 搜索上游失败";
        return sse("error", Map.of("code", "upstream_error", "message", message));
    }

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
            String role = msg.getRole().trim().toLowerCase();
            // 仅转发 user/assistant，禁止把 system 历史交给 Agent（防提示注入提权）
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            messages.add(MemeAgentStreamRequestDTO.ChatMessage.builder()
                    .role(role)
                    .content(msg.getContent().trim())
                    .build());
        }
        AiBusinessContextAssembler.AssembledContext assembled = businessContextAssembler.assemble(query);
        return MemeAgentStreamRequestDTO.builder()
                .requestId(requestId)
                .sessionId(String.valueOf(sessionId))
                .query(query)
                .messages(messages)
                .context(MemeAgentStreamRequestDTO.BusinessContext.builder()
                        .userId(String.valueOf(userId))
                        .locale("zh-CN")
                        .hintMemeIds(assembled.hintMemeIds())
                        .extra(assembled.extra())
                        .build())
                .maxTokens(Math.min(
                        Math.max(64, properties.getAi().getMaxOutputTokens()),
                        2048))
                .build();
    }

    private AiChatSession resolveSession(Long userId, String sessionIdRaw, String query) {
        if (StringUtils.hasText(sessionIdRaw)) {
            Long sessionId = AuthContext.parseLongId(sessionIdRaw, "sessionId");
            AiChatSession existing = sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                    .eq(AiChatSession::getId, sessionId)
                    .eq(AiChatSession::getUserId, userId)
                    .eq(AiChatSession::getIsDeleted, 0));
            if (existing != null) {
                return existing;
            }
            throw new BizException(Result.CODE_NOT_FOUND, "会话不存在或已删除");
        }
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setTitle(truncateTitle(query));
        session.setIsDeleted(0);
        session.setCreateTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    private void touchSession(Long sessionId) {
        AiChatSession patch = new AiChatSession();
        patch.setId(sessionId);
        patch.setUpdateTime(LocalDateTime.now());
        sessionMapper.updateById(patch);
    }

    private List<AiChatMessage> loadHistory(Long sessionId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AiChatMessage>()
                .eq(AiChatMessage::getSessionId, sessionId)
                .orderByAsc(AiChatMessage::getCreateTime));
    }

    private void saveMessage(Long sessionId, String role, String content, String requestId) {
        AiChatMessage message = new AiChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setRequestId(requestId);
        message.setTokenEstimate(tokenBudgetTrimmer.estimateTokens(content));
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);
    }

    private static String truncateTitle(String query) {
        if (!StringUtils.hasText(query)) {
            return "新对话";
        }
        String trimmed = query.trim();
        return trimmed.length() <= 32 ? trimmed : trimmed.substring(0, 32) + "…";
    }
}
