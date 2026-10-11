package com.sakana.just_because_meme_understands_you.modules.ai.application.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiStreamTaskService;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AiChatSession;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.AiChatMessageMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.AiChatSessionMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.application.assembler.AiChatViewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatSessionServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final Long SESSION_ID = 99L;

    @Mock
    private AiChatSessionMapper sessionMapper;
    @Mock
    private AiChatMessageMapper messageMapper;
    @Mock
    private AiChatViewMapper viewMapper;
    @Mock
    private IAiStreamTaskService taskService;

    private AiChatSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"), AiChatSession.class);
        service = new AiChatSessionServiceImpl();
        ReflectionTestUtils.setField(service, "sessionMapper", sessionMapper);
        ReflectionTestUtils.setField(service, "messageMapper", messageMapper);
        ReflectionTestUtils.setField(service, "aiChatViewMapper", viewMapper);
        ReflectionTestUtils.setField(service, "aiStreamTaskService", taskService);
        AiChatSession session = new AiChatSession();
        session.setId(SESSION_ID);
        session.setUserId(USER_ID);
        session.setIsDeleted(0);
        when(sessionMapper.selectOne(any())).thenReturn(session);
    }

    @Test
    void cancelsSessionTaskAfterSuccessfulSoftDelete() {
        when(sessionMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        service.softDeleteSession(USER_ID, SESSION_ID);

        verify(taskService).cancelBySession(USER_ID, SESSION_ID);
    }

    @Test
    void doesNotCancelTaskWhenSoftDeleteFails() {
        when(sessionMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThrows(BizException.class, () -> service.softDeleteSession(USER_ID, SESSION_ID));

        verify(taskService, never()).cancelBySession(anyLong(), anyLong());
    }
}
