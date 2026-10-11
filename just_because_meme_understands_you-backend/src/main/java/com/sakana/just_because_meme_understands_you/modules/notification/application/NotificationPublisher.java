package com.sakana.just_because_meme_understands_you.modules.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.data.DataStatusConstants;
import com.sakana.just_because_meme_understands_you.modules.notification.domain.NotificationConstants;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.notification.domain.entity.UserNotification;
import com.sakana.just_because_meme_understands_you.modules.notification.infrastructure.persistence.mapper.UserNotificationMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 站内消息写入（由业务域在互动发生后调用）。
 */
@Slf4j
@Service
public class NotificationPublisher {

    private static final int TITLE_MAX = 128;
    private static final int CONTENT_MAX = 512;
    private static final int UNREAD = 0;

    @Resource
    private UserNotificationMapper userNotificationMapper;

    @Resource
    private ObjectMapper objectMapper;

    /** 由 Outbox 消费端调用；重复消费同一事件时由唯一键保证幂等。 */
    public void publishCommentNotification(CommentNotificationCommand command) {
        if (command == null || command.sourceEventId() == null || command.sourceEventId() <= 0
                || command.receiverUserId() == null || command.receiverUserId() <= 0
                || command.actorUserId() == null || command.actorUserId() <= 0
                || command.memeId() == null || command.memeId() <= 0
                || command.commentId() == null || command.commentId() <= 0) {
            throw new IllegalArgumentException("评论通知命令字段不完整");
        }
        if (Objects.equals(command.receiverUserId(), command.actorUserId())) {
            return;
        }
        String title = switch (command.type() == null ? "" : command.type().trim()) {
            case NotificationConstants.TYPE_COMMENT -> "评论了你的梗";
            case NotificationConstants.TYPE_REPLY -> "回复了你的评论";
            default -> throw new IllegalArgumentException("不支持的评论通知类型");
        };

        LocalDateTime now = LocalDateTime.now();
        UserNotification row = new UserNotification();
        row.setSourceEventId(command.sourceEventId());
        row.setUserId(command.receiverUserId());
        row.setActorId(command.actorUserId());
        row.setType(command.type().trim());
        row.setTitle(truncate(title, TITLE_MAX));
        row.setContent(truncate(StringUtils.hasText(command.content())
                ? command.content().trim() : title, CONTENT_MAX));
        row.setTargetType(NotificationConstants.TARGET_MEME);
        row.setTargetId(command.memeId());
        row.setRefId(command.commentId());
        row.setExtraJson(buildMemeExtraJson(command.memeName(), command.memeCover(), command.rootId()));
        row.setGroupKey((NotificationConstants.TYPE_REPLY.equals(command.type())
                ? "reply_comment:" : "comment_meme:") + command.commentId());
        row.setIsRead(UNREAD);
        row.setIsDeleted(DataStatusConstants.NOT_DELETED);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        try {
            userNotificationMapper.insert(row);
            log.info("已写入评论通知 eventId={} id={} type={} receiver={} commentId={}",
                    command.sourceEventId(), row.getId(), command.type(),
                    command.receiverUserId(), command.commentId());
        } catch (DuplicateKeyException duplicate) {
            log.debug("评论通知事件已处理，跳过重复投递 eventId={}", command.sourceEventId());
        }
    }

    /** 管理员完成内容举报后告知举报人处理结果。 */
    public void publishReportResolution(Long receiverUserId, Meme meme, Long memeId,
                                        Long reportId, String result, String note) {
        if (receiverUserId == null || receiverUserId <= 0 || memeId == null || memeId <= 0
                || reportId == null || reportId <= 0) {
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            UserNotification row = new UserNotification();
            row.setUserId(receiverUserId);
            row.setActorId(null);
            row.setType("audit");
            row.setTitle("举报处理结果");
            String outcome = "OFFLINED".equals(result) ? "你举报的梗已下架。"
                    : "COMMENT_HIDDEN".equals(result) ? "你举报的评论已隐藏。"
                    : "你举报的内容经核查暂不处理。";
            row.setContent(outcome + (StringUtils.hasText(note) ? "处理说明：" + note.trim() : ""));
            row.setTargetType(NotificationConstants.TARGET_MEME);
            row.setTargetId(memeId);
            row.setRefId(reportId);
            row.setExtraJson(buildMemeExtraJson(meme, null));
            row.setGroupKey("meme_report:" + reportId);
            row.setIsRead(UNREAD);
            row.setIsDeleted(DataStatusConstants.NOT_DELETED);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            userNotificationMapper.insert(row);
        } catch (Exception e) {
            log.error("写入举报处理通知失败 reportId={} receiver={}", reportId, receiverUserId, e);
        }
    }

    /** 中文注释：在修改稿审核事务中写入结果通知，驳回时包含审核说明。 */
    public void publishMemeEditReview(Long receiverUserId, Long reviewerUserId, Long revisionId,
                                      Long memeId, String memeName, String memeCover,
                                      String result, String reviewNote) {
        if (receiverUserId == null || receiverUserId <= 0 || revisionId == null || revisionId <= 0
                || memeId == null || memeId <= 0
                || (!"APPROVED".equals(result) && !"REJECTED".equals(result) && !"CANCELED".equals(result))) {
            return;
        }
        String title = "APPROVED".equals(result) ? "梗修改已通过"
                : "REJECTED".equals(result) ? "梗修改未通过" : "梗修改审核已取消";
        String content = "APPROVED".equals(result) ? "你提交的梗修改已通过审核。"
                : "REJECTED".equals(result) ? "你提交的梗修改未通过审核。"
                : "你提交的梗修改因梗状态变化已取消审核。";
        if (StringUtils.hasText(reviewNote)) {
            content += "审核说明：" + reviewNote.trim();
        }

        LocalDateTime now = LocalDateTime.now();
        UserNotification row = new UserNotification();
        row.setUserId(receiverUserId);
        row.setActorId(reviewerUserId);
        row.setType(NotificationConstants.TYPE_REVIEW);
        row.setTitle(truncate(title, TITLE_MAX));
        row.setContent(truncate(content, CONTENT_MAX));
        row.setTargetType(NotificationConstants.TARGET_MEME);
        row.setTargetId(memeId);
        row.setRefId(revisionId);
        row.setExtraJson(buildMemeEditExtraJson(memeName, memeCover, revisionId));
        row.setGroupKey("meme_edit_revision:" + revisionId);
        row.setIsRead(UNREAD);
        row.setIsDeleted(DataStatusConstants.NOT_DELETED);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        userNotificationMapper.insert(row);
    }

    private String buildMemeEditExtraJson(String memeName, String memeCover, Long revisionId) {
        try {
            Map<String, String> extra = new LinkedHashMap<>();
            if (StringUtils.hasText(memeName)) {
                extra.put("memeName", memeName.trim());
            }
            if (StringUtils.hasText(memeCover)) {
                extra.put("memeCover", memeCover.trim());
            }
            extra.put("revisionId", String.valueOf(revisionId));
            return objectMapper.writeValueAsString(extra);
        } catch (Exception exception) {
            log.debug("序列化梗修改审核通知 extra_json 失败 revisionId={}", revisionId);
            return null;
        }
    }

    private String buildMemeExtraJson(Meme meme, Long rootId) {
        return buildMemeExtraJson(
                meme == null ? null : meme.getName(),
                meme == null ? null : meme.getImage(),
                rootId);
    }

    private String buildMemeExtraJson(String memeName, String memeCover, Long rootId) {
        try {
            Map<String, String> extra = new LinkedHashMap<>();
            if (StringUtils.hasText(memeName)) {
                extra.put("memeName", memeName.trim());
            }
            if (StringUtils.hasText(memeCover)) {
                extra.put("memeCover", memeCover.trim());
            }
            if (rootId != null && rootId > 0) {
                extra.put("rootId", String.valueOf(rootId));
            }
            if (extra.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(extra);
        } catch (Exception e) {
            log.debug("序列化消息 extra_json 失败");
            return null;
        }
    }

    private static String truncate(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= max) {
            return trimmed;
        }
        return trimmed.substring(0, Math.max(0, max - 1)) + "…";
    }
}
