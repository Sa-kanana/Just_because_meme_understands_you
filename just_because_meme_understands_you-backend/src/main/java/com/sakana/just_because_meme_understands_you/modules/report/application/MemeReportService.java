package com.sakana.just_because_meme_understands_you.modules.report.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminReportVO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.pagination.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminMemeService;
import com.sakana.just_because_meme_understands_you.modules.comment.domain.entity.MemeComment;
import com.sakana.just_because_meme_understands_you.modules.comment.infrastructure.persistence.mapper.MemeCommentMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.application.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.notification.application.NotificationPublisher;
import com.sakana.just_because_meme_understands_you.modules.report.domain.entity.CommunityReport;
import com.sakana.just_because_meme_understands_you.modules.report.application.assembler.AdminReportViewMapper;
import com.sakana.just_because_meme_understands_you.modules.report.infrastructure.persistence.mapper.CommunityReportMapper;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.user.domain.UserDisplayPolicy;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class MemeReportService {
    private static final Set<String> REASONS = Set.of("SPAM", "ABUSE", "COPYRIGHT", "PORNOGRAPHY", "VIOLENCE", "OTHER");
    private static final Set<String> REPORT_STATUSES = Set.of("PENDING", "RESOLVED");
    private static final Set<String> MEME_RESULTS = Set.of("DISMISSED", "OFFLINED");
    private static final Set<String> COMMENT_RESULTS = Set.of("DISMISSED", "COMMENT_HIDDEN");
    private static final String TARGET_MEME = "MEME";
    private static final String TARGET_COMMENT = "COMMENT";

    @Resource private MemeMapper memeMapper;
    @Resource private MemeCommentMapper commentMapper;
    @Resource private CommunityReportMapper communityReportMapper;
    @Resource private AdminReportViewMapper adminReportViewMapper;
    @Resource private UserMapper userMapper;
    @Resource private IAdminMemeService adminMemeService;
    @Resource private NotificationPublisher notificationPublisher;

    /** 提交公开梗举报，统一写入社区举报表。 */
    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long memeId, Long reporterId, String reason, String detail) {
        validateSubmission(memeId, reporterId, reason, detail);
        Meme meme = memeMapper.selectById(memeId);
        if (meme == null || !Integer.valueOf(MemeVisibilitySupport.STATUS_NORMAL).equals(meme.getStatus())) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或不可举报");
        }
        if (reporterId.equals(meme.getUserId())) {
            throw new BizException(Result.CODE_FORBIDDEN, "不能举报自己的梗");
        }
        return insertReport(TARGET_MEME, memeId, memeId, null, reporterId, reason, detail,
                "你已举报过这条梗");
    }

    /** 举报评论时同时确认评论未删除且其所属梗仍公开。 */
    @Transactional(rollbackFor = Exception.class)
    public Long submitComment(Long commentId, Long reporterId, String reason, String detail) {
        validateSubmission(commentId, reporterId, reason, detail);
        MemeComment comment = commentMapper.selectById(commentId);
        if (comment == null || !Integer.valueOf(0).equals(comment.getIsDeleted())) {
            throw new BizException(Result.CODE_NOT_FOUND, "评论不存在或不可举报");
        }
        Meme meme = memeMapper.selectById(comment.getMemeId());
        if (meme == null || !Integer.valueOf(MemeVisibilitySupport.STATUS_NORMAL).equals(meme.getStatus())) {
            throw new BizException(Result.CODE_NOT_FOUND, "评论所属梗不存在或不可举报");
        }
        if (reporterId.equals(comment.getUserId())) {
            throw new BizException(Result.CODE_FORBIDDEN, "不能举报自己的评论");
        }
        return insertReport(TARGET_COMMENT, commentId, comment.getMemeId(), commentId,
                reporterId, reason, detail, "你已举报过这条评论");
    }

    public PageVO<AdminReportVO> page(Integer page, Integer size, String status) {
        if (status != null && !status.isBlank() && !REPORT_STATUSES.contains(status)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "举报状态不合法");
        }
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);
        LambdaQueryWrapper<CommunityReport> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) query.eq(CommunityReport::getStatus, status);
        query.orderByDesc(CommunityReport::getCreatedAt).orderByDesc(CommunityReport::getId);
        Page<CommunityReport> rows = communityReportMapper.selectPage(new Page<>(pageNo, pageSize), query);
        List<AdminReportVO> items = rows.getRecords().stream().map(this::toAdminVO).toList();
        PageVO<AdminReportVO> output = new PageVO<>();
        output.setList(items);
        output.setPage(pageNo);
        output.setSize(pageSize);
        output.setTotal(rows.getTotal());
        output.setHasMore((long) pageNo * pageSize < rows.getTotal());
        return output;
    }

    /** 举报处理与下架/隐藏动作处于同一事务，失败时保留待处理状态。 */
    @Transactional(rollbackFor = Exception.class)
    public AdminReportVO resolve(Long id, Long adminId, String result, String note) {
        if (id == null || id <= 0 || adminId == null || adminId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "处理参数不合法");
        }
        String safeNote = note == null ? "" : note.trim();
        if (safeNote.length() > 500) throw new BizException(Result.CODE_BAD_REQUEST, "处理备注不能超过 500 字");
        CommunityReport row = communityReportMapper.selectById(id);
        if (row == null) throw new BizException(Result.CODE_NOT_FOUND, "举报不存在");
        if (!isValidResult(row.getTargetType(), result)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "处理结果与举报对象类型不匹配");
        }
        if (communityReportMapper.resolvePending(id, result, adminId,
                safeNote.isEmpty() ? null : safeNote, LocalDateTime.now()) != 1) {
            throw new BizException(Result.CODE_BAD_REQUEST, "举报已处理");
        }
        if (TARGET_MEME.equals(row.getTargetType()) && "OFFLINED".equals(result)) {
            adminMemeService.offline(row.getMemeId());
        } else if (TARGET_COMMENT.equals(row.getTargetType()) && "COMMENT_HIDDEN".equals(result)) {
            Long commentId = row.getCommentId() != null ? row.getCommentId() : row.getTargetId();
            if (commentMapper.hideById(commentId) != 1) {
                MemeComment existing = commentMapper.selectById(commentId);
                if (existing == null || !Objects.equals(existing.getIsDeleted(), 1)) {
                    throw new BizException(Result.CODE_BAD_REQUEST, "评论已删除或无法隐藏");
                }
            }
        }
        Meme meme = memeMapper.selectById(row.getMemeId());
        notificationPublisher.publishReportResolution(row.getReporterUserId(), meme, row.getMemeId(),
                row.getId(), result, safeNote);
        AdminReportVO vo = new AdminReportVO();
        vo.setId(id);
        vo.setTargetType(row.getTargetType());
        vo.setTargetId(row.getTargetId());
        vo.setMemeId(row.getMemeId());
        vo.setCommentId(row.getCommentId());
        vo.setStatus("RESOLVED");
        vo.setResult(result);
        vo.setHandledBy(adminId);
        vo.setHandlingNote(safeNote.isEmpty() ? null : safeNote);
        vo.setHandledAt(LocalDateTime.now());
        return vo;
    }

    private Long insertReport(String targetType, Long targetId, Long memeId, Long commentId,
                              Long reporterId, String reason, String detail, String duplicateMessage) {
        CommunityReport row = new CommunityReport();
        row.setTargetType(targetType);
        row.setTargetId(targetId);
        row.setMemeId(memeId);
        row.setCommentId(commentId);
        row.setReporterUserId(reporterId);
        row.setReason(reason);
        String safeDetail = detail == null ? "" : detail.trim();
        row.setDetail(safeDetail.isEmpty() ? null : safeDetail);
        row.setStatus("PENDING");
        row.setCreatedAt(LocalDateTime.now());
        try {
            communityReportMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new BizException(Result.CODE_BAD_REQUEST, duplicateMessage, e);
        }
        return row.getId();
    }

    private void validateSubmission(Long targetId, Long reporterId, String reason, String detail) {
        if (targetId == null || targetId <= 0 || reporterId == null || reporterId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "举报参数不合法");
        }
        if (reason == null || !REASONS.contains(reason)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "举报原因不合法");
        }
        String safeDetail = detail == null ? "" : detail.trim();
        if (safeDetail.length() > 500 || ("OTHER".equals(reason) && safeDetail.isEmpty())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "补充说明须在 500 字内，其他原因必须填写说明");
        }
    }

    private boolean isValidResult(String targetType, String result) {
        if (result == null) return false;
        if (TARGET_MEME.equals(targetType)) return MEME_RESULTS.contains(result);
        if (TARGET_COMMENT.equals(targetType)) return COMMENT_RESULTS.contains(result);
        return false;
    }

    private AdminReportVO toAdminVO(CommunityReport row) {
        AdminReportVO vo = adminReportViewMapper.toAdminReportVO(row);
        Meme meme = memeMapper.selectById(row.getMemeId());
        if (meme != null) {
            vo.setMemeName(meme.getName());
            vo.setMemeStatus(meme.getStatus());
            if (TARGET_MEME.equals(row.getTargetType())) {
                vo.setTargetAuthorId(meme.getUserId());
            }
        }
        if (TARGET_COMMENT.equals(row.getTargetType())) {
            Long commentId = row.getCommentId() != null ? row.getCommentId() : row.getTargetId();
            MemeComment comment = commentMapper.selectById(commentId);
            if (comment != null) {
                vo.setCommentId(commentId);
                vo.setCommentContent(comment.getContent());
                vo.setCommentAuthorId(comment.getUserId());
                vo.setTargetAuthorId(comment.getUserId());
            }
        }
        User reporter = userMapper.selectById(row.getReporterUserId());
        if (reporter != null) vo.setReporterName(UserDisplayPolicy.displayName(reporter, "匿名用户"));
        if (vo.getTargetAuthorId() != null) {
            User author = userMapper.selectById(vo.getTargetAuthorId());
            if (author != null) {
                String authorName = UserDisplayPolicy.displayName(author, "匿名用户");
                vo.setTargetAuthorName(authorName);
                if (TARGET_COMMENT.equals(row.getTargetType())) vo.setCommentAuthorName(authorName);
            }
        }
        return vo;
    }
}
