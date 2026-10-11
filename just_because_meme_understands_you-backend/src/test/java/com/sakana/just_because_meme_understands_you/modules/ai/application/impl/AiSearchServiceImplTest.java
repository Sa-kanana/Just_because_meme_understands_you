package com.sakana.just_because_meme_understands_you.modules.ai.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.api.user.dto.AiSearchStreamRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.application.AiSearchPreparation;
import com.sakana.just_because_meme_understands_you.modules.ai.application.support.AiAnswerCache;
import com.sakana.just_because_meme_understands_you.modules.ai.application.support.AiRateLimiter;
import com.sakana.just_because_meme_understands_you.modules.ai.application.support.TokenBudgetTrimmer;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AiChatMessage;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AiChatSession;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentStreamRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.AiChatMessageMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.AiChatSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AiSearchServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 99L;
    private static final String QUERY = "梗搜索";

    @Mock
    private MemeAgentClient memeAgentClient;
    @Mock
    private AiChatSessionMapper sessionMapper;
    @Mock
    private AiChatMessageMapper messageMapper;
    @Mock
    private TokenBudgetTrimmer tokenBudgetTrimmer;
    @Mock
    private AiAnswerCache aiAnswerCache;
    @Mock
    private AiRateLimiter aiRateLimiter;
    private MemeAgentProperties properties;
    private AiSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new MemeAgentProperties();
        service = new AiSearchServiceImpl(
                memeAgentClient,
                properties,
                sessionMapper,
                messageMapper,
                tokenBudgetTrimmer,
                aiAnswerCache,
                aiRateLimiter,
                new ObjectMapper()
        );

        AiChatSession session = new AiChatSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setIsDeleted(0);
        lenient().when(memeAgentClient.isConfigured()).thenReturn(true);
        lenient().when(aiRateLimiter.tryAcquire(USER_ID)).thenReturn(true);
        lenient().when(sessionMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(session);
        lenient().when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        lenient().when(tokenBudgetTrimmer.trim(any(), anyInt())).thenReturn(List.of());
        when(tokenBudgetTrimmer.estimateTokens(anyString())).thenReturn(1);
        lenient().when(aiAnswerCache.fingerprint(any())).thenReturn("test-context-fingerprint");
        lenient().when(aiAnswerCache.get(USER_ID, QUERY, "test-context-fingerprint")).thenReturn(Optional.empty());
    }

    @Test
    void prepareSearchReturnsSessionAndRequestIdsBeforeSubscription() {
        when(memeAgentClient.stream(any())).thenReturn(Flux.never());

        AiSearchPreparation preparation = service.prepareSearch(USER_ID, request());

        assertEquals(SESSION_ID, preparation.sessionId());
        assertEquals("request-1", preparation.requestId());
        verifyNoAssistantPersistence();
    }

    @Test
    void aiSearchDoesNotSendMysqlKeywordCandidatesAlongsideVectorRetrieval() {
        when(memeAgentClient.stream(any())).thenReturn(Flux.just(
                event("meta", "{\"request_id\":\"request-1\",\"retrieved\":[]}"),
                event("token", "{\"text\":\"回答\"}"),
                event("done", "{\"finish_reason\":\"stop\"}")));

        collectEvents(request());

        ArgumentCaptor<MemeAgentStreamRequestDTO> captor =
                ArgumentCaptor.forClass(MemeAgentStreamRequestDTO.class);
        verify(memeAgentClient).stream(captor.capture());
        assertEquals(List.of(), captor.getValue().getContext().getHintMemeIds());
        assertEquals(Map.of(), captor.getValue().getContext().getExtra());
    }

    @Test
    void cancellingPreparationFluxDoesNotPersistBufferedAnswer() {
        when(memeAgentClient.stream(any())).thenReturn(Flux.just(
                event("token", "{\"text\":\"不完整答案\"}"),
                event("done", "{\"finish_reason\":\"stop\"}")));

        AiSearchPreparation preparation = service.prepareSearch(USER_ID, request());
        preparation.events().take(3).collectList().block(Duration.ofSeconds(3));

        verifyNoAssistantPersistence();
    }

    @Test
    void savesAndCachesOnlyAfterNormalDone() {
        when(memeAgentClient.stream(any())).thenReturn(Flux.just(
                event("meta", "{\"request_id\":\"request-1\"}"),
                event("cite", "{\"meme_id\":\"60\",\"score\":0.9,\"title\":\"不明觉厉\"}"),
                event("token", "{\"text\":\"完整答案\"}"),
                event("done", "{\"finish_reason\":\"stop\"}")));
        assertEquals(130000L, properties.getAi().getStreamIdleTimeoutMs());

        List<ServerSentEvent<String>> events = collectEvents(request());
        assertEquals(List.of("session", "meta", "cite", "token", "done"), eventNames(events));

        verifyAssistantSaved("完整答案");
        verify(aiAnswerCache).put(USER_ID, QUERY, "test-context-fingerprint", "完整答案");
    }

    @Test
    void savesCompleteFallbackToHistoryButDoesNotCacheIt() {
        when(memeAgentClient.stream(any())).thenReturn(Flux.just(
                event("token", "{\"text\":\"候选梗摘要\"}"),
                event("done", "{\"finish_reason\":\"fallback_retrieval_unavailable\"}")));

        List<ServerSentEvent<String>> events = collectEvents(request());
        assertEquals(List.of("session", "token", "done"), eventNames(events));

        verifyAssistantSaved("候选梗摘要");
        verify(aiAnswerCache, never()).put(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void doesNotCacheUnknownAnswerWithoutVectorEvidence() {
        when(memeAgentClient.stream(any())).thenReturn(Flux.just(
                event("meta", "{\"request_id\":\"request-1\",\"retrieved\":[]}"),
                event("token", "{\"text\":\"不知道。\"}"),
                event("done", "{\"finish_reason\":\"stop\"}")));

        collectEvents(request());

        verify(aiAnswerCache, never()).put(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void discardsBufferedAnswerWhenPythonSendsErrorEvent() {
        when(memeAgentClient.stream(any())).thenReturn(Flux.just(
                event("token", "{\"text\":\"不完整片段\"}"),
                event("error", "{\"code\":\"llm_error\",\"message\":\"通用错误\"}")));

        List<ServerSentEvent<String>> events = collectEvents(request());
        assertEquals(List.of("session", "token", "error"), eventNames(events));

        verifyNoAssistantPersistence();
    }

    @Test
    void discardsBufferedAnswerAfterTransportFailureAndReturnsGenericError() {
        when(memeAgentClient.stream(any())).thenReturn(Flux.concat(
                Flux.just(event("token", "{\"text\":\"不完整片段\"}")),
                Flux.error(new IllegalStateException("provider secret detail"))));

        List<ServerSentEvent<String>> events = collectEvents(request());
        assertEquals(List.of("session", "token", "error"), eventNames(events));
        assertFalse(events.get(2).data().contains("provider secret detail"));

        verifyNoAssistantPersistence();
    }

    @Test
    void idleTimeoutDiscardsBufferedAnswerAndReturnsGenericError() {
        properties.getAi().setStreamIdleTimeoutMs(20L);
        when(memeAgentClient.stream(any())).thenReturn(Flux.concat(
                Flux.just(event("token", "{\"text\":\"不完整片段\"}")),
                Flux.never()));

        List<ServerSentEvent<String>> events = collectEvents(request());

        assertEquals(List.of("session", "token", "error"), eventNames(events));
        assertFalse(events.get(2).data().contains("TimeoutException"));
        verifyNoAssistantPersistence();
    }

    @Test
    void normalCompletionWithoutDoneDoesNotPersistOrCache() {
        when(memeAgentClient.stream(any())).thenReturn(Flux.just(
                event("token", "{\"text\":\"不完整片段\"}")));

        List<ServerSentEvent<String>> events = collectEvents(request());
        assertEquals(List.of("session", "token"), eventNames(events));

        verifyNoAssistantPersistence();
    }

    @Test
    void conversationHistoryDoesNotReuseQueryOnlyCachedAnswer() {
        AiChatMessage history = new AiChatMessage();
        history.setRole("user");
        history.setStatus("COMPLETED");
        history.setContent("前一轮问题，限定这次回答的上下文");
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(history));
        when(tokenBudgetTrimmer.trim(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(aiAnswerCache.get(USER_ID, QUERY)).thenReturn(Optional.of("不相关的旧答案"));
        when(memeAgentClient.stream(any())).thenReturn(Flux.just(
                event("token", "{\"text\":\"结合上下文的新答案\"}"),
                event("done", "{\"finish_reason\":\"stop\"}")));

        List<ServerSentEvent<String>> events = collectEvents(request());

        assertTrue(events.stream().anyMatch(event -> event.data().contains("结合上下文的新答案")));
        assertFalse(events.stream().anyMatch(event -> event.data().contains("不相关的旧答案")));
        verify(memeAgentClient).stream(any());
        ArgumentCaptor<Object> contextCaptor = ArgumentCaptor.forClass(Object.class);
        verify(aiAnswerCache).fingerprint(contextCaptor.capture());
        Map<?, ?> cacheContext = (Map<?, ?>) contextCaptor.getValue();
        assertTrue(cacheContext.get("pipeline_version").toString().contains("meme-search-v5"));
        assertTrue(cacheContext.get("messages").toString().contains("前一轮问题"));
    }

    @Test
    void sendsOnlyCompletedMessagesAsNextRequestContext() {
        List<AiChatMessage> history = List.of(
                historyMessage("user", "已完成的问题", "COMPLETED"),
                historyMessage("assistant", "已完成的回答", "COMPLETED"),
                historyMessage("assistant", "停止后保留的片段", "STOPPED"),
                historyMessage("assistant", "失败的回答", "FAILED"),
                historyMessage("assistant", "仍在生成的内容", "GENERATING")
        );
        when(messageMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(history);
        when(tokenBudgetTrimmer.trim(any(), anyInt())).thenAnswer(invocation -> invocation.getArgument(0));
        when(memeAgentClient.stream(any())).thenReturn(Flux.just(
                event("token", "{\"text\":\"新回答\"}"),
                event("done", "{\"finish_reason\":\"stop\"}")));

        collectEvents(request());

        ArgumentCaptor<MemeAgentStreamRequestDTO> captor =
                ArgumentCaptor.forClass(MemeAgentStreamRequestDTO.class);
        verify(memeAgentClient).stream(captor.capture());
        assertEquals(List.of("已完成的问题", "已完成的回答"), captor.getValue().getMessages().stream()
                .map(MemeAgentStreamRequestDTO.ChatMessage::getContent)
                .toList());
    }

    private static AiChatMessage historyMessage(String role, String content, String status) {
        AiChatMessage message = new AiChatMessage();
        message.setRole(role);
        message.setContent(content);
        message.setStatus(status);
        return message;
    }

    @Test
    void streamsAnswersWithoutLatencyTelemetry(CapturedOutput output) {
        when(memeAgentClient.stream(any())).thenReturn(Flux.just(
                event("token", "{\"text\":\"完整答案\"}"),
                event("done", "{\"finish_reason\":\"stop\"}")));

        collectEvents(request());

        assertFalse(output.getOut().contains("ai_stream_metrics"));
    }

    @Test
    void persistsAssistantMessageOnBoundedElasticScheduler() {
        when(memeAgentClient.stream(any())).thenReturn(Flux.just(
                event("token", "{\"text\":\"完整答案\"}"),
                event("done", "{\"finish_reason\":\"stop\"}")));
        AtomicReference<String> assistantWriteThread = new AtomicReference<>();
        doAnswer(invocation -> {
            AiChatMessage message = invocation.getArgument(0);
            if ("assistant".equals(message.getRole())) {
                assistantWriteThread.set(Thread.currentThread().getName());
            }
            return 1;
        }).when(messageMapper).insert(any(AiChatMessage.class));

        collectEvents(request());

        assertTrue(assistantWriteThread.get().startsWith("boundedElastic"));
    }

    @Test
    void replaysCachedAnswersWithoutLatencyTelemetry(CapturedOutput output) {
        doReturn(Optional.of("缓存答案"))
                .when(aiAnswerCache).get(USER_ID, QUERY, "test-context-fingerprint");

        List<ServerSentEvent<String>> events = collectEvents(request());

        assertEquals(List.of("session", "meta", "token", "done"), eventNames(events));
        assertTrue(events.stream().anyMatch(event -> event.data().contains("缓存答案")));
        assertFalse(output.getOut().contains("ai_stream_metrics"));
        verify(memeAgentClient, never()).stream(any());
    }

    private AiSearchStreamRequestDTO request() {
        AiSearchStreamRequestDTO request = new AiSearchStreamRequestDTO();
        request.setQuery(QUERY);
        request.setSessionId(String.valueOf(SESSION_ID));
        request.setRequestId("request-1");
        return request;
    }

    private static ServerSentEvent<String> event(String name, String data) {
        return ServerSentEvent.<String>builder().event(name).data(data).build();
    }

    private List<ServerSentEvent<String>> collectEvents(AiSearchStreamRequestDTO request) {
        return service.prepareSearch(USER_ID, request).events()
                .collectList().block(Duration.ofSeconds(3));
    }

    private static List<String> eventNames(List<ServerSentEvent<String>> events) {
        return events.stream().map(ServerSentEvent::event).toList();
    }

    private void verifyAssistantSaved(String answer) {
        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageMapper, atLeastOnce()).insert(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(message ->
                "assistant".equals(message.getRole()) && answer.equals(message.getContent())));
    }

    private void verifyNoAssistantPersistence() {
        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageMapper, atLeastOnce()).insert(captor.capture());
        assertFalse(captor.getAllValues().stream().anyMatch(message ->
                "assistant".equals(message.getRole())));
        verify(aiAnswerCache, never()).put(anyLong(), anyString(), anyString());
    }

    @Test
    void persistsStoppedPartialAnswerWithStoppedStatus() {
        service.persistStoppedAnswer(SESSION_ID, "request-1", "已经生成的部分内容");

        ArgumentCaptor<AiChatMessage> captor = ArgumentCaptor.forClass(AiChatMessage.class);
        verify(messageMapper).insert(captor.capture());
        AiChatMessage savedMessage = captor.getValue();
        assertEquals("assistant", savedMessage.getRole());
        assertEquals("已经生成的部分内容", savedMessage.getContent());
        assertEquals("STOPPED", savedMessage.getStatus());
    }
}
