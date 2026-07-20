package com.sakana.just_because_meme_understands_you.service.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
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
import com.sakana.just_because_meme_understands_you.service.ai.support.AiRateLimiter;
import com.sakana.just_because_meme_understands_you.service.ai.support.TokenBudgetTrimmer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    public Flux<String> streamSearch(Long userId, AiSearchStreamRequestDTO request) {
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

        saveMessage(sessionId, "user", query, requestId);

        Optional<String> cached = aiAnswerCache.get(userId, query);
        if (cached.isPresent()) {
            return replayCachedAnswer(requestId, cached.get());
        }

        List<AiChatMessage> history = loadHistory(sessionId);
        List<AiChatMessage> trimmed = tokenBudgetTrimmer.trim(history, properties.getAi().getMaxHistoryTokens());

        MemeAgentStreamRequestDTO agentRequest = buildAgentRequest(
                userId, sessionId, requestId, query, trimmed);

        AtomicReference<StringBuilder> assistantBuffer = new AtomicReference<>(new StringBuilder());

        return memeAgentClient.stream(agentRequest)
                .doOnNext(chunk -> accumulateAssistant(chunk, assistantBuffer))
                .doOnComplete(() -> {
                    String answer = assistantBuffer.get().toString();
                    if (StringUtils.hasText(answer)) {
                        saveMessage(sessionId, "assistant", answer, requestId);
                        aiAnswerCache.put(userId, query, answer);
                    }
                    touchSession(sessionId, query);
                })
                .doOnError(e -> log.error("AI stream error userId={} sessionId={}", userId, sessionId, e));
    }

    private Flux<String> replayCachedAnswer(String requestId, String answer) {
        String meta = "event: meta\ndata: {\"request_id\":\"" + requestId + "\",\"retrieved\":[]}\n\n";
        StringBuilder tokens = new StringBuilder();
        int step = 32;
        for (int i = 0; i < answer.length(); i += step) {
            String piece = answer.substring(i, Math.min(i + step, answer.length()));
            tokens.append("event: token\ndata: {\"text\":")
                    .append(aiAnswerCache.toJson(piece))
                    .append("}\n\n");
        }
        String done = "event: done\ndata: {\"finish_reason\":\"cache\",\"usage\":{}}\n\n";
        return Flux.just(meta, tokens.toString(), done);
    }

    private void accumulateAssistant(String chunk, AtomicReference<StringBuilder> buffer) {
        if (!StringUtils.hasText(chunk)) {
            return;
        }
        try {
            for (String line : chunk.split("\n")) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String json = line.substring(5).trim();
                if (!StringUtils.hasText(json)) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(json);
                if (node.has("text")) {
                    buffer.get().append(node.get("text").asText(""));
                }
            }
        } catch (Exception e) {
            log.debug("Skip non-json sse chunk", e);
        }
    }

    private MemeAgentStreamRequestDTO buildAgentRequest(Long userId,
                                                        Long sessionId,
                                                        String requestId,
                                                        String query,
                                                        List<AiChatMessage> trimmed) {
        List<MemeAgentStreamRequestDTO.ChatMessage> messages = new ArrayList<>();
        for (AiChatMessage msg : trimmed) {
            messages.add(MemeAgentStreamRequestDTO.ChatMessage.builder()
                    .role(msg.getRole())
                    .content(msg.getContent())
                    .build());
        }
        return MemeAgentStreamRequestDTO.builder()
                .requestId(requestId)
                .sessionId(String.valueOf(sessionId))
                .query(query)
                .messages(messages)
                .context(MemeAgentStreamRequestDTO.BusinessContext.builder()
                        .userId(String.valueOf(userId))
                        .locale("zh-CN")
                        .build())
                .maxTokens(properties.getAi().getMaxOutputTokens())
                .build();
    }

    private AiChatSession resolveSession(Long userId, String sessionIdRaw, String query) {
        if (StringUtils.hasText(sessionIdRaw)) {
            try {
                Long sessionId = Long.parseLong(sessionIdRaw.trim());
                AiChatSession existing = sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                        .eq(AiChatSession::getId, sessionId)
                        .eq(AiChatSession::getUserId, userId)
                        .eq(AiChatSession::getIsDeleted, 0));
                if (existing != null) {
                    return existing;
                }
            } catch (NumberFormatException ignored) {
                // fall through to create
            }
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

    private void touchSession(Long sessionId, String query) {
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
