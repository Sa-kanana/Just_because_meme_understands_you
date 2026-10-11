package com.sakana.just_because_meme_understands_you.modules.report.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminReportVO;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminMemeService;
import com.sakana.just_because_meme_understands_you.modules.comment.domain.entity.MemeComment;
import com.sakana.just_because_meme_understands_you.modules.comment.infrastructure.persistence.mapper.MemeCommentMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.notification.application.NotificationPublisher;
import com.sakana.just_because_meme_understands_you.modules.report.domain.entity.CommunityReport;
import com.sakana.just_because_meme_understands_you.modules.report.application.assembler.AdminReportViewMapper;
import org.mapstruct.factory.Mappers;
import com.sakana.just_because_meme_understands_you.modules.report.infrastructure.persistence.mapper.CommunityReportMapper;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminReportQueueContextTest {
    @Test
    void queueIncludesCommentMemeReporterAndAuthorContext() {
        MemeMapper memes = mock(MemeMapper.class);
        MemeCommentMapper comments = mock(MemeCommentMapper.class);
        CommunityReportMapper reports = mock(CommunityReportMapper.class);
        UserMapper users = mock(UserMapper.class);
        CommunityReport row = new CommunityReport();
        row.setId(41L);
        row.setTargetType("COMMENT");
        row.setTargetId(31L);
        row.setCommentId(31L);
        row.setMemeId(7L);
        row.setReporterUserId(3L);
        row.setReason("ABUSE");
        row.setStatus("PENDING");
        Page<CommunityReport> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        doReturn(page).when(reports).selectPage(any(Page.class), any());
        when(memes.selectById(7L)).thenReturn(new Meme().setId(7L).setUserId(9L).setName("梗标题").setStatus(1));
        MemeComment comment = new MemeComment();
        comment.setId(31L);
        comment.setUserId(8L);
        comment.setContent("被举报的评论原文");
        when(comments.selectById(31L)).thenReturn(comment);
        User reporter = new User(); reporter.setId(3L); reporter.setNickname("举报人");
        User author = new User(); author.setId(8L); author.setNickname("评论作者");
        when(users.selectById(3L)).thenReturn(reporter);
        when(users.selectById(8L)).thenReturn(author);

        MemeReportService service = new MemeReportService();
        ReflectionTestUtils.setField(service, "adminReportViewMapper", Mappers.getMapper(AdminReportViewMapper.class));
        ReflectionTestUtils.setField(service, "memeMapper", memes);
        ReflectionTestUtils.setField(service, "commentMapper", comments);
        ReflectionTestUtils.setField(service, "communityReportMapper", reports);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "adminMemeService", mock(IAdminMemeService.class));
        ReflectionTestUtils.setField(service, "notificationPublisher", mock(NotificationPublisher.class));

        AdminReportVO result = service.page(1, 10, "PENDING").getList().get(0);
        assertEquals("COMMENT", result.getTargetType());
        assertEquals("梗标题", result.getMemeName());
        assertEquals("被举报的评论原文", result.getCommentContent());
        assertEquals("评论作者", result.getTargetAuthorName());
        assertEquals("举报人", result.getReporterName());
    }

    @Test
    void missingCommentDoesNotAttributeMemeAuthorAsCommentAuthor() {
        MemeMapper memes = mock(MemeMapper.class);
        MemeCommentMapper comments = mock(MemeCommentMapper.class);
        CommunityReportMapper reports = mock(CommunityReportMapper.class);
        UserMapper users = mock(UserMapper.class);
        CommunityReport row = new CommunityReport();
        row.setId(42L);
        row.setTargetType("COMMENT");
        row.setTargetId(32L);
        row.setCommentId(32L);
        row.setMemeId(7L);
        row.setReporterUserId(3L);
        row.setReason("ABUSE");
        row.setStatus("PENDING");
        Page<CommunityReport> page = new Page<>(1, 10);
        page.setRecords(List.of(row));
        page.setTotal(1);
        doReturn(page).when(reports).selectPage(any(Page.class), any());
        when(memes.selectById(7L)).thenReturn(new Meme().setId(7L).setUserId(9L).setName("梗标题").setStatus(1));
        when(comments.selectById(32L)).thenReturn(null);

        MemeReportService service = new MemeReportService();
        ReflectionTestUtils.setField(service, "adminReportViewMapper", Mappers.getMapper(AdminReportViewMapper.class));
        ReflectionTestUtils.setField(service, "memeMapper", memes);
        ReflectionTestUtils.setField(service, "commentMapper", comments);
        ReflectionTestUtils.setField(service, "communityReportMapper", reports);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "adminMemeService", mock(IAdminMemeService.class));
        ReflectionTestUtils.setField(service, "notificationPublisher", mock(NotificationPublisher.class));

        AdminReportVO result = service.page(1, 10, "PENDING").getList().get(0);
        assertNull(result.getTargetAuthorId());
        assertNull(result.getTargetAuthorName());
        assertNull(result.getCommentAuthorId());
        assertNull(result.getCommentAuthorName());
    }
}
