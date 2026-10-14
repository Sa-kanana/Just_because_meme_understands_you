package com.sakana.just_because_meme_understands_you.modules.comment.application;

import com.sakana.just_because_meme_understands_you.modules.comment.domain.entity.MemeComment;
import com.sakana.just_because_meme_understands_you.modules.comment.infrastructure.persistence.mapper.MemeCommentMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemeCommentModerationServiceTest {

    /** 评论持久层模拟对象。 */
    @Mock
    private MemeCommentMapper memeCommentMapper;

    /** 梗图评论计数应用契约模拟对象。 */
    @Mock
    private MemeCatalogPort memeCatalogPort;

    /** 被测评论审核应用服务。 */
    @InjectMocks
    private MemeCommentModerationService moderationService;

    @Test
    void hideRootCommentAlsoHidesRepliesAndDecrementsAllActiveComments() {
        MemeComment root = comment(100L, 7L, 0L);
        when(memeCommentMapper.selectById(100L)).thenReturn(root);
        when(memeCommentMapper.hideById(100L)).thenReturn(1);
        when(memeCommentMapper.hideRepliesByRootId(100L)).thenReturn(2);
        when(memeCatalogPort.decrementCommentCount(7L, 3)).thenReturn(true);

        assertEquals(3, moderationService.hideComment(100L));

        verify(memeCommentMapper).hideRepliesByRootId(100L);
        verify(memeCatalogPort).decrementCommentCount(7L, 3);
    }

    @Test
    void hideReplyDecrementsRootReplyCountAndOneMemeComment() {
        MemeComment reply = comment(101L, 7L, 100L);
        when(memeCommentMapper.selectById(101L)).thenReturn(reply);
        when(memeCommentMapper.hideById(101L)).thenReturn(1);
        when(memeCommentMapper.decrementReplyCount(100L)).thenReturn(1);
        when(memeCatalogPort.decrementCommentCount(7L, 1)).thenReturn(true);

        assertEquals(1, moderationService.hideComment(101L));

        verify(memeCommentMapper).decrementReplyCount(100L);
        verify(memeCatalogPort).decrementCommentCount(7L, 1);
    }

    private MemeComment comment(long id, long memeId, long rootId) {
        MemeComment comment = new MemeComment();
        comment.setId(id);
        comment.setMemeId(memeId);
        comment.setRootId(rootId);
        comment.setIsDeleted(0);
        return comment;
    }
}
