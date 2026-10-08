package com.sakana.just_because_meme_understands_you.modules.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.data.DataStatusConstants;
import com.sakana.just_because_meme_understands_you.modules.notification.domain.NotificationConstants;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.notification.domain.entity.UserNotification;
import com.sakana.just_because_meme_understands_you.modules.notification.infrastructure.persistence.mapper.UserNotificationMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * 回复评论通知：通知被回复评论的作者。
     */
    public void publishCommentReply(Long receiverUserId,
                                    Long actorUserId,
                                    Meme meme,
                                    Long memeId,
                                    Long replyCommentId,
                                    Long rootId,
                                    String replyContent) {
        publishInteract(
                receiverUserId,
                actorUserId,
                NotificationConstants.TYPE_REPLY,
                "回复了你的评论",
                replyContent,
                meme,
                memeId,
                replyCommentId,
                rootId,
                "reply_comment:" + replyCommentId
        );
    }

    /**
     * 根评论通知：通知梗作者有人评论了 TA 的梗。
     */
    public void publishMemeComment(Long receiverUserId,
                                   Long actorUserId,
                                   Meme meme,
                                   Long memeId,
                                   Long commentId,
                                   String commentContent) {
        publishInteract(
                receiverUserId,
                actorUserId,
                NotificationConstants.TYPE_COMMENT,
                "评论了你的梗",
                commentContent,
                meme,
                memeId,
                commentId,
                commentId,
                "comment_meme:" + commentId
        );
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

    private void publishInteract(Long receiverUserId,
                                 Long actorUserId,
                                 String type,
                                 String title,
                                 String content,
                                 Meme meme,
                                 Long memeId,
                                 Long refId,
                                 Long rootId,
                                 String groupKey) {
        if (receiverUserId == null || receiverUserId <= 0
                || actorUserId == null || actorUserId <= 0
                || memeId == null || memeId <= 0
                || refId == null || refId <= 0
                || !StringUtils.hasText(type)) {
            log.debug("跳过消息写入：参数不完整 type={} receiver={} actor={} memeId={} refId={}",
                    type, receiverUserId, actorUserId, memeId, refId);
            return;
        }
        if (Objects.equals(receiverUserId, actorUserId)) {
            log.debug("跳过消息写入：自己触发自己 type={} userId={}", type, receiverUserId);
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            UserNotification row = new UserNotification();
            row.setUserId(receiverUserId);
            row.setActorId(actorUserId);
            row.setType(type.trim());
            row.setTitle(truncate(title, TITLE_MAX));
            row.setContent(truncate(StringUtils.hasText(content) ? content.trim() : title, CONTENT_MAX));
            row.setTargetType(NotificationConstants.TARGET_MEME);
            row.setTargetId(memeId);
            row.setRefId(refId);
            row.setExtraJson(buildMemeExtraJson(meme, rootId));
            row.setGroupKey(groupKey);
            row.setIsRead(UNREAD);
            row.setIsDeleted(DataStatusConstants.NOT_DELETED);
            row.setCreateTime(now);
            row.setUpdateTime(now);
            userNotificationMapper.insert(row);
            log.info("已写入站内消息 type={} id={} receiver={} actor={} memeId={} refId={}",
                    type, row.getId(), receiverUserId, actorUserId, memeId, refId);
        } catch (Exception e) {
            // 消息失败不影响主业务（评论已成功）；打 error 便于排查
            log.error("写入站内消息失败 type={} receiver={} actor={} memeId={} refId={}",
                    type, receiverUserId, actorUserId, memeId, refId, e);
        }
    }

    private String buildMemeExtraJson(Meme meme, Long rootId) {
        try {
            Map<String, String> extra = new LinkedHashMap<>();
            if (meme != null) {
                if (StringUtils.hasText(meme.getName())) {
                    extra.put("memeName", meme.getName().trim());
                }
                if (StringUtils.hasText(meme.getImage())) {
                    extra.put("memeCover", meme.getImage().trim());
                }
            }
            if (rootId != null && rootId > 0) {
                extra.put("rootId", String.valueOf(rootId));
            }
            if (extra.isEmpty()) {
                return null;
            }
            return objectMapper.writeValueAsString(extra);
        } catch (Exception e) {
            log.debug("序列化消息 extra_json 失败, memeId={}", meme != null ? meme.getId() : null);
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
