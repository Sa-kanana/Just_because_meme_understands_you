package com.sakana.just_because_meme_understands_you.modules.comment.application;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.comment.application.impl.MemeCommentServiceImpl;
import com.sakana.just_because_meme_understands_you.modules.comment.contract.CommentModerationPort;
import com.sakana.just_because_meme_understands_you.modules.comment.application.support.MemeCommentSupport;
import com.sakana.just_because_meme_understands_you.modules.comment.domain.entity.MemeComment;
import com.sakana.just_because_meme_understands_you.modules.comment.infrastructure.persistence.mapper.MemeCommentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemeCommentDeleteTest {

    private MemeCommentMapper comments;
    private CommentModerationPort moderation;
    private MemeCommentServiceImpl service;

    @BeforeEach
    void setUp() {
        comments = mock(MemeCommentMapper.class);
        moderation = mock(CommentModerationPort.class);
        service = new MemeCommentServiceImpl();
        ReflectionTestUtils.setField(service, "memeCommentMapper", comments);
        ReflectionTestUtils.setField(service, "commentModerationPort", moderation);
        ReflectionTestUtils.setField(service, "commentSupport", mock(MemeCommentSupport.class));
    }

    @Test
    void ownerCanSoftDeleteTheirComment() {
        MemeComment comment = new MemeComment();
        comment.setId(21L);
        comment.setUserId(7L);
        comment.setIsDeleted(MemeCommentSupport.NOT_DELETED);
        when(comments.selectById(21L)).thenReturn(comment);
        when(moderation.hideComment(21L)).thenReturn(1);

        service.deleteComment(7L, 21L);

        verify(moderation).hideComment(21L);
    }

    @Test
    void anotherUserCannotDeleteTheComment() {
        MemeComment comment = new MemeComment();
        comment.setId(21L);
        comment.setUserId(8L);
        comment.setIsDeleted(MemeCommentSupport.NOT_DELETED);
        when(comments.selectById(21L)).thenReturn(comment);

        assertEquals(403, assertThrows(BizException.class, () -> service.deleteComment(7L, 21L)).getCode());
        verify(moderation, never()).hideComment(21L);
    }

    @Test
    void missingOrDeletedCommentReturnsNotFound() {
        when(comments.selectById(21L)).thenReturn(null);

        assertEquals(404, assertThrows(BizException.class, () -> service.deleteComment(7L, 21L)).getCode());
        verify(moderation, never()).hideComment(21L);
    }
}
