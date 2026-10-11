package com.sakana.just_because_meme_understands_you.modules.ai.application.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.AiChatMessageVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.AiChatSessionVO;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AiChatMessage;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AiChatSession;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiChatViewMapperTest {

    private final AiChatViewMapper mapper = Mappers.getMapper(AiChatViewMapper.class);

    @Test
    void mapsSessionFieldsToView() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 8, 9, 15);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 8, 10, 20);
        AiChatSession source = new AiChatSession();
        source.setId(71L);
        source.setTitle("会话标题");
        source.setCreateTime(createdAt);
        source.setUpdateTime(updatedAt);

        AiChatSessionVO result = mapper.toSessionVO(source);

        assertEquals(71L, result.getId());
        assertEquals("会话标题", result.getTitle());
        assertEquals(createdAt, result.getCreateTime());
        assertEquals(updatedAt, result.getUpdateTime());
    }

    @Test
    void mapsMessageFieldsToView() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 8, 11, 25);
        AiChatMessage source = new AiChatMessage();
        source.setId(81L);
        source.setSessionId(71L);
        source.setRole("assistant");
        source.setContent("回答内容");
        source.setRequestId("request-81");
        source.setTokenEstimate(14);
        source.setCreateTime(createdAt);

        AiChatMessageVO result = mapper.toMessageVO(source);

        assertEquals(81L, result.getId());
        assertEquals(71L, result.getSessionId());
        assertEquals("assistant", result.getRole());
        assertEquals("回答内容", result.getContent());
        assertEquals("request-81", result.getRequestId());
        assertEquals(14, result.getTokenEstimate());
        assertEquals(createdAt, result.getCreateTime());
    }
}
