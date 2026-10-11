package com.sakana.just_because_meme_understands_you.modules.comment.application;

import com.sakana.just_because_meme_understands_you.api.user.dto.MemeCommentCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.comment.application.impl.MemeCommentServiceImpl;
import com.sakana.just_because_meme_understands_you.modules.comment.application.support.MemeCommentSupport;
import com.sakana.just_because_meme_understands_you.modules.comment.domain.entity.MemeComment;
import com.sakana.just_because_meme_understands_you.modules.comment.infrastructure.persistence.mapper.MemeCommentMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.application.IMemeService;
import com.sakana.just_because_meme_understands_you.modules.meme.application.MemeBloomFilterService;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.moderation.application.SensitiveWordFilterService;
import com.sakana.just_because_meme_understands_you.modules.user.application.safety.UserSafetyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MemeCommentSafetyTest {
    @Test
    void incrementsMemeCommentCountInTheCommentTransaction() {
        MemeCommentMapper comments = mock(MemeCommentMapper.class);
        MemeCommentSupport support = mock(MemeCommentSupport.class);
        IMemeService memes = mock(IMemeService.class);
        MemeBloomFilterService bloom = mock(MemeBloomFilterService.class);
        SensitiveWordFilterService filter = mock(SensitiveWordFilterService.class);
        MemeCatalogPort memeCatalogPort = mock(MemeCatalogPort.class);
        MemeCommentAsyncHandler asyncHandler = mock(MemeCommentAsyncHandler.class);
        MemeCommentServiceImpl service = new MemeCommentServiceImpl();
        ReflectionTestUtils.setField(service, "memeCommentMapper", comments);
        ReflectionTestUtils.setField(service, "commentSupport", support);
        ReflectionTestUtils.setField(service, "memeService", memes);
        ReflectionTestUtils.setField(service, "memeBloomFilterService", bloom);
        ReflectionTestUtils.setField(service, "sensitiveWordFilterService", filter);
        ReflectionTestUtils.setField(service, "memeCatalogPort", memeCatalogPort);
        ReflectionTestUtils.setField(service, "memeCommentAsyncHandler", asyncHandler);

        Meme meme = new Meme().setId(7L).setStatus(1).setComments(2);
        when(bloom.mightContain(7L)).thenReturn(true);
        when(memes.getById(7L)).thenReturn(meme);
        when(memeCatalogPort.incrementCommentCount(7L)).thenReturn(true);
        when(filter.filterForInsert("hello")).thenReturn("hello");
        when(support.toCreateResponseVO(any(MemeComment.class)))
                .thenReturn(new com.sakana.just_because_meme_understands_you.api.user.vo.MemeCommentCreateResponseVO());

        MemeCommentCreateRequestDTO request = new MemeCommentCreateRequestDTO();
        request.setMemeId(7L);
        request.setContent("hello");

        service.createComment(3L, request);

        verify(comments).insert(any(MemeComment.class));
        verify(memeCatalogPort).incrementCommentCount(7L);
    }

    @Test
    void rejectsReplyToBlockedAuthorBeforeInsertingComment() {
        MemeCommentMapper comments = mock(MemeCommentMapper.class);
        MemeCommentSupport support = mock(MemeCommentSupport.class);
        IMemeService memes = mock(IMemeService.class);
        MemeBloomFilterService bloom = mock(MemeBloomFilterService.class);
        UserSafetyService safety = mock(UserSafetyService.class);
        SensitiveWordFilterService filter = mock(SensitiveWordFilterService.class);
        MemeCommentServiceImpl service = new MemeCommentServiceImpl();
        ReflectionTestUtils.setField(service, "memeCommentMapper", comments);
        ReflectionTestUtils.setField(service, "commentSupport", support);
        ReflectionTestUtils.setField(service, "memeService", memes);
        ReflectionTestUtils.setField(service, "memeBloomFilterService", bloom);
        ReflectionTestUtils.setField(service, "userSafetyService", safety);
        ReflectionTestUtils.setField(service, "sensitiveWordFilterService", filter);

        Meme meme = new Meme().setId(7L).setUserId(9L).setStatus(1);
        when(bloom.mightContain(7L)).thenReturn(true);
        when(memes.getById(7L)).thenReturn(meme);
        when(support.parseCommentRefId("5", "rootId")).thenReturn(5L);
        when(support.parseCommentRefId("6", "parentId")).thenReturn(6L);
        when(support.isValidReplyParent(any(MemeComment.class), eq(5L))).thenReturn(true);
        when(filter.filterForInsert("reply")).thenReturn("reply");
        MemeComment root = comment(5L, 7L, 11L, 0L);
        MemeComment parent = comment(6L, 7L, 10L, 5L);
        when(comments.selectById(5L)).thenReturn(root);
        when(comments.selectById(6L)).thenReturn(parent);
        doThrow(new BizException(403, "屏蔽关系不允许互动"))
                .when(safety).assertInteractionAllowed(3L, 10L);
        MemeCommentCreateRequestDTO request = new MemeCommentCreateRequestDTO();
        request.setMemeId(7L);
        request.setRootId("5");
        request.setParentId("6");
        request.setContent("reply");

        assertThrows(BizException.class, () -> service.createComment(3L, request));
        verify(safety).assertInteractionAllowed(3L, 9L);
        verify(safety).assertInteractionAllowed(3L, 10L);
        verify(comments, never()).insert(any(MemeComment.class));
    }

    private static MemeComment comment(Long id, Long memeId, Long userId, Long rootId) {
        MemeComment comment = new MemeComment();
        comment.setId(id);
        comment.setMemeId(memeId);
        comment.setUserId(userId);
        comment.setRootId(rootId);
        comment.setIsDeleted(0);
        return comment;
    }
}
