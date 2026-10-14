package com.sakana.just_because_meme_understands_you.modules.report.application;

import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminReportVO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminMemeService;
import com.sakana.just_because_meme_understands_you.modules.comment.domain.entity.MemeComment;
import com.sakana.just_because_meme_understands_you.modules.comment.contract.CommentModerationPort;
import com.sakana.just_because_meme_understands_you.modules.comment.infrastructure.persistence.mapper.MemeCommentMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.notification.application.NotificationPublisher;
import com.sakana.just_because_meme_understands_you.modules.report.domain.entity.CommunityReport;
import com.sakana.just_because_meme_understands_you.modules.report.application.assembler.AdminReportViewMapper;
import org.mapstruct.factory.Mappers;
import com.sakana.just_because_meme_understands_you.modules.report.infrastructure.persistence.mapper.CommunityReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MemeCommentReportServiceTest {
    private MemeMapper memes;
    private MemeCommentMapper comments;
    private CommentModerationPort moderation;
    private CommunityReportMapper reports;
    private MemeReportService service;
    private IAdminMemeService adminMemes;

    @BeforeEach
    void setUp() {
        memes = mock(MemeMapper.class);
        comments = mock(MemeCommentMapper.class);
        moderation = mock(CommentModerationPort.class);
        reports = mock(CommunityReportMapper.class);
        adminMemes = mock(IAdminMemeService.class);
        service = new MemeReportService();
        ReflectionTestUtils.setField(service, "adminReportViewMapper", Mappers.getMapper(AdminReportViewMapper.class));
        ReflectionTestUtils.setField(service, "memeMapper", memes);
        ReflectionTestUtils.setField(service, "commentMapper", comments);
        ReflectionTestUtils.setField(service, "commentModerationPort", moderation);
        ReflectionTestUtils.setField(service, "communityReportMapper", reports);
        ReflectionTestUtils.setField(service, "adminMemeService", adminMemes);
        ReflectionTestUtils.setField(service, "notificationPublisher", mock(NotificationPublisher.class));
    }

    @Test
    void storesCommentAndParentMemeForUnifiedModerationQueue() {
        when(comments.selectById(31L)).thenReturn(comment(31L, 7L, 9L, 0));
        when(memes.selectById(7L)).thenReturn(new Meme().setId(7L).setUserId(8L).setStatus(1));
        when(reports.insert(any(CommunityReport.class))).thenAnswer(call -> {
            CommunityReport report = call.getArgument(0);
            report.setId(42L);
            return 1;
        });

        assertEquals(42L, service.submitComment(31L, 3L, "ABUSE", "辱骂"));
        var captor = org.mockito.ArgumentCaptor.forClass(CommunityReport.class);
        verify(reports).insert(captor.capture());
        assertEquals("COMMENT", captor.getValue().getTargetType());
        assertEquals(31L, captor.getValue().getTargetId());
        assertEquals(7L, captor.getValue().getMemeId());
        assertEquals("PENDING", captor.getValue().getStatus());
    }

    @Test
    void rejectsOwnOrDeletedCommentAndCommentOnInvisibleMeme() {
        when(comments.selectById(31L)).thenReturn(comment(31L, 7L, 3L, 0));
        when(memes.selectById(7L)).thenReturn(new Meme().setId(7L).setUserId(8L).setStatus(1));
        assertEquals(403, assertThrows(BizException.class,
                () -> service.submitComment(31L, 3L, "ABUSE", "")).getCode());

        when(comments.selectById(31L)).thenReturn(comment(31L, 7L, 9L, 1));
        assertEquals(404, assertThrows(BizException.class,
                () -> service.submitComment(31L, 3L, "ABUSE", "")).getCode());

        when(comments.selectById(31L)).thenReturn(comment(31L, 7L, 9L, 0));
        when(memes.selectById(7L)).thenReturn(new Meme().setId(7L).setUserId(8L).setStatus(3));
        assertEquals(404, assertThrows(BizException.class,
                () -> service.submitComment(31L, 3L, "ABUSE", "")).getCode());
        verify(reports, never()).insert(any(CommunityReport.class));
    }

    @Test
    void mapsUniqueTargetCollisionToBusinessError() {
        when(comments.selectById(31L)).thenReturn(comment(31L, 7L, 9L, 0));
        when(memes.selectById(7L)).thenReturn(new Meme().setId(7L).setUserId(8L).setStatus(1));
        when(reports.insert(any(CommunityReport.class))).thenThrow(new DuplicateKeyException("unique"));
        assertEquals(400, assertThrows(BizException.class,
                () -> service.submitComment(31L, 3L, "ABUSE", "")).getCode());
    }

    @Test
    void hidesCommentAndRecordsAdminDecision() {
        CommunityReport report = report(42L, "COMMENT", 31L, 7L);
        when(reports.selectById(42L)).thenReturn(report);
        when(reports.resolvePending(eq(42L), eq("COMMENT_HIDDEN"), eq(5L), eq("处理"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(moderation.hideComment(31L)).thenReturn(1);

        AdminReportVO result = service.resolve(42L, 5L, "COMMENT_HIDDEN", "处理");
        assertEquals("RESOLVED", result.getStatus());
        verify(moderation).hideComment(31L);
        verify(reports).resolvePending(eq(42L), eq("COMMENT_HIDDEN"), eq(5L), eq("处理"), any(LocalDateTime.class));
        verify(adminMemes, never()).offline(anyLong());
    }

    @Test
    void recordsHideDecisionWhenCommentWasAlreadySoftDeleted() {
        CommunityReport report = report(42L, "COMMENT", 31L, 7L);
        when(reports.selectById(42L)).thenReturn(report);
        when(reports.resolvePending(eq(42L), eq("COMMENT_HIDDEN"), eq(5L), isNull(), any(LocalDateTime.class)))
                .thenReturn(1);
        when(moderation.hideComment(31L)).thenReturn(0);
        when(comments.selectById(31L)).thenReturn(comment(31L, 7L, 9L, 1));

        assertEquals("COMMENT_HIDDEN", service.resolve(42L, 5L, "COMMENT_HIDDEN", "").getResult());
    }

    @Test
    void rejectsMemeOnlyResolutionForComment() {
        when(reports.selectById(42L)).thenReturn(report(42L, "COMMENT", 31L, 7L));
        assertEquals(400, assertThrows(BizException.class,
                () -> service.resolve(42L, 5L, "OFFLINED", "")).getCode());
        verify(reports, never()).resolvePending(anyLong(), anyString(), anyLong(), any(), any());
        verify(moderation, never()).hideComment(anyLong());
    }

    private static MemeComment comment(Long id, Long memeId, Long userId, Integer deleted) {
        MemeComment row = new MemeComment();
        row.setId(id);
        row.setMemeId(memeId);
        row.setUserId(userId);
        row.setIsDeleted(deleted);
        return row;
    }

    private static CommunityReport report(Long id, String type, Long targetId, Long memeId) {
        CommunityReport row = new CommunityReport();
        row.setId(id);
        row.setTargetType(type);
        row.setTargetId(targetId);
        row.setCommentId("COMMENT".equals(type) ? targetId : null);
        row.setMemeId(memeId);
        row.setReporterUserId(3L);
        row.setStatus("PENDING");
        return row;
    }
}
