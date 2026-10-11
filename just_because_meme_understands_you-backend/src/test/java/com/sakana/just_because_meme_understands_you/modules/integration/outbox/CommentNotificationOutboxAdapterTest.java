package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.modules.comment.contract.CommentNotificationEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

class CommentNotificationOutboxAdapterTest {

    @Test
    void serializesCommentNotificationIntoTransactionalOutboxEvent() throws Exception {
        OutboxEventWriter writer = mock(OutboxEventWriter.class);
        CommentNotificationOutboxAdapter adapter =
                new CommentNotificationOutboxAdapter(writer, new ObjectMapper());
        CommentNotificationEvent event = new CommentNotificationEvent(
                CommentNotificationEvent.Type.MEME_COMMENTED,
                18L,
                3L,
                45L,
                90L,
                90L,
                "梗名",
                "https://example.test/cover.png",
                "评论内容");

        adapter.append(event);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(writer).appendEvent(eq("COMMENT"), eq(90L),
                eq("COMMENT_NOTIFICATION_MEME_COMMENTED"), payload.capture());
        JsonNode json = new ObjectMapper().readTree(payload.getValue());
        assertEquals("MEME_COMMENTED", json.get("type").asText());
        assertEquals(18L, json.get("receiverUserId").asLong());
        assertEquals(45L, json.get("memeId").asLong());
        assertEquals(90L, json.get("commentId").asLong());
        assertEquals("评论内容", json.get("content").asText());
    }
}
