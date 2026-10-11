package com.sakana.just_because_meme_understands_you.modules.report.application;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminMemeService;
import com.sakana.just_because_meme_understands_you.modules.notification.application.NotificationPublisher;
import com.sakana.just_because_meme_understands_you.modules.report.domain.entity.CommunityReport;
import com.sakana.just_because_meme_understands_you.modules.report.application.assembler.AdminReportViewMapper;
import org.mapstruct.factory.Mappers;
import com.sakana.just_because_meme_understands_you.modules.report.infrastructure.persistence.mapper.CommunityReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DuplicateKeyException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemeReportServiceTest {
    private MemeMapper memeMapper;
    private CommunityReportMapper reportMapper;
    private MemeReportService service;
    private IAdminMemeService adminMemeService;
    private NotificationPublisher notificationPublisher;

    @BeforeEach
    void setUp() {
        memeMapper = mock(MemeMapper.class);
        reportMapper = mock(CommunityReportMapper.class);
        adminMemeService = mock(IAdminMemeService.class);
        notificationPublisher = mock(NotificationPublisher.class);
        service = new MemeReportService();
        ReflectionTestUtils.setField(service, "adminReportViewMapper", Mappers.getMapper(AdminReportViewMapper.class));
        ReflectionTestUtils.setField(service, "memeMapper", memeMapper);
        ReflectionTestUtils.setField(service, "communityReportMapper", reportMapper);
        ReflectionTestUtils.setField(service, "adminMemeService", adminMemeService);
        ReflectionTestUtils.setField(service, "notificationPublisher", notificationPublisher);
    }

    @Test
    void rejectsOwnMeme() {
        when(memeMapper.selectById(7L)).thenReturn(new Meme().setId(7L).setUserId(3L).setStatus(1));
        BizException error = assertThrows(BizException.class,
                () -> service.submit(7L, 3L, "SPAM", "广告"));
        assertEquals(403, error.getCode());
        verify(reportMapper, never()).insert(any(CommunityReport.class));
    }

    @Test
    void rejectsInvisibleMeme() {
        when(memeMapper.selectById(7L)).thenReturn(new Meme().setId(7L).setUserId(9L).setStatus(3));
        BizException error = assertThrows(BizException.class,
                () -> service.submit(7L, 3L, "SPAM", "广告"));
        assertEquals(404, error.getCode());
        verify(reportMapper, never()).insert(any(CommunityReport.class));
    }

    @Test
    void rejectsUnknownReason() {
        when(memeMapper.selectById(7L)).thenReturn(new Meme().setId(7L).setUserId(9L).setStatus(1));
        BizException error = assertThrows(BizException.class,
                () -> service.submit(7L, 3L, "WRONG", "说明"));
        assertEquals(400, error.getCode());
        verify(reportMapper, never()).insert(any(CommunityReport.class));
    }

    @Test
    void savesValidReportAndReturnsId() {
        when(memeMapper.selectById(7L)).thenReturn(new Meme().setId(7L).setUserId(9L).setStatus(1));
        when(reportMapper.insert(any(CommunityReport.class))).thenAnswer(invocation -> {
            CommunityReport row = invocation.getArgument(0);
            row.setId(21L);
            return 1;
        });
        assertEquals(21L, service.submit(7L, 3L, "SPAM", "广告"));
    }

    @Test
    void duplicateReportReturnsBusinessError() {
        when(memeMapper.selectById(7L)).thenReturn(new Meme().setId(7L).setUserId(9L).setStatus(1));
        when(reportMapper.insert(any(CommunityReport.class))).thenThrow(new DuplicateKeyException("unique"));
        BizException error = assertThrows(BizException.class,
                () -> service.submit(7L, 3L, "SPAM", "广告"));
        assertEquals(400, error.getCode());
    }

    @Test
    void resolvesOnlyPendingAndOfflinesReportedMeme() {
        CommunityReport row = new CommunityReport();
        row.setId(21L);
        row.setTargetType("MEME");
        row.setTargetId(7L);
        row.setMemeId(7L);
        row.setStatus("PENDING");
        when(reportMapper.selectById(21L)).thenReturn(row);
        when(reportMapper.resolvePending(eq(21L), eq("OFFLINED"), eq(5L), isNull(), any(LocalDateTime.class)))
                .thenReturn(1);
        assertEquals("RESOLVED", service.resolve(21L, 5L, "OFFLINED", "").getStatus());
        verify(adminMemeService).offline(7L);
    }

    @Test
    void cannotProcessSameReportTwice() {
        CommunityReport row = new CommunityReport();
        row.setId(21L);
        row.setTargetType("MEME");
        row.setTargetId(7L);
        row.setMemeId(7L);
        when(reportMapper.selectById(21L)).thenReturn(row);
        BizException error = assertThrows(BizException.class,
                () -> service.resolve(21L, 5L, "OFFLINED", ""));
        assertEquals(400, error.getCode());
        verifyNoInteractions(adminMemeService);
    }
}

