package com.sakana.just_because_meme_understands_you.api;

import com.sakana.just_because_meme_understands_you.api.user.controller.AiSearchController;
import com.sakana.just_because_meme_understands_you.api.user.dto.AiSearchStreamRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.AiStreamTaskVO;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiStreamTaskService;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskSnapshot;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskStatus;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSearchControllerTest {

    private static final Long USER_ID = 7L;
    private static final AiStreamTaskSnapshot SNAPSHOT = new AiStreamTaskSnapshot(
            "task-1", USER_ID, 99L, "request-1", AiStreamTaskStatus.GENERATING,
            1_000L, 601_000L, 5L);

    @Mock
    private IAiStreamTaskService taskService;
    @Mock
    private HttpServletRequest httpRequest;
    @InjectMocks
    private AiSearchController controller;

    @Test
    void createSearchUsesAuthenticatedUserAndMapsTaskMetadata() {
        when(httpRequest.getAttribute(AuthContext.ATTR_USER_ID)).thenReturn(USER_ID);
        AiSearchStreamRequestDTO request = new AiSearchStreamRequestDTO();
        request.setQuery("梗搜索");
        request.setRequestId("request-1");
        when(taskService.submit(USER_ID, request)).thenReturn(SNAPSHOT);

        AiStreamTaskVO task = controller.createSearch(request, httpRequest).getData();

        assertEquals("task-1", task.taskId());
        assertEquals(99L, task.sessionId());
        assertEquals("request-1", task.requestId());
        assertEquals(AiStreamTaskStatus.GENERATING, task.status());
        assertEquals(601_000L, task.expiresAtMillis());
        verify(taskService).submit(USER_ID, request);
    }

    @Test
    void currentSearchUsesAuthenticatedUser() {
        when(httpRequest.getAttribute(AuthContext.ATTR_USER_ID)).thenReturn(USER_ID);
        when(taskService.getCurrent(USER_ID)).thenReturn(Optional.of(SNAPSHOT));

        AiStreamTaskVO task = controller.getCurrent(httpRequest).getData();

        assertEquals("task-1", task.taskId());
        verify(taskService).getCurrent(USER_ID);
    }

    @Test
    void eventsUseAuthenticatedUserAndRequestedCursor() {
        when(httpRequest.getAttribute(AuthContext.ATTR_USER_ID)).thenReturn(USER_ID);
        when(taskService.events(USER_ID, "task-1", 4L)).thenReturn(Flux.just(
                ServerSentEvent.<String>builder().id("5").event("token").data("{}").build()));

        ServerSentEvent<String> event = controller.events("task-1", 4L, httpRequest).blockFirst();

        assertEquals("5", event.id());
        assertEquals("token", event.event());
        verify(taskService).events(USER_ID, "task-1", 4L);
    }

    @Test
    void cancelUsesAuthenticatedUser() {
        when(httpRequest.getAttribute(AuthContext.ATTR_USER_ID)).thenReturn(USER_ID);
        AiStreamTaskSnapshot cancelled = new AiStreamTaskSnapshot(
                "task-1", USER_ID, 99L, "request-1", AiStreamTaskStatus.STOPPED,
                1_000L, 601_000L, 5L);
        when(taskService.cancel(USER_ID, "task-1")).thenReturn(cancelled);

        AiStreamTaskVO task = controller.cancel("task-1", httpRequest).getData();

        assertEquals(AiStreamTaskStatus.STOPPED, task.status());
        verify(taskService).cancel(USER_ID, "task-1");
    }

    @Test
    void unauthenticatedRequestsAreRejectedBeforeCallingTaskService() {
        when(httpRequest.getAttribute(AuthContext.ATTR_USER_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> controller.getCurrent(httpRequest));
    }
}
