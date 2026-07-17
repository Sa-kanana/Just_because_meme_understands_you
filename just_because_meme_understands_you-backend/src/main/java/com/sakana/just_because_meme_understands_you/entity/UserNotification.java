package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户站内消息，对应表 user_notification。
 * is_read / is_deleted：0=否，1=是（软删语义）。
 */
@Data
@TableName("user_notification")
public class UserNotification {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 接收人 */
    private Long userId;

    /** 触发人（系统消息可为空） */
    private Long actorId;

    /** 消息类型，如 like / comment / follow / system */
    private String type;

    private String title;

    private String content;

    /** 跳转目标类型：meme / user / comment 等 */
    private String targetType;

    private Long targetId;

    /** 附属引用（如评论 id） */
    private Long refId;

    /** 扩展 JSON，如 memeName / memeCover */
    @TableField("extra_json")
    private String extraJson;

    /** 聚合键（可选） */
    private String groupKey;

    @TableField("is_read")
    private Integer isRead;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
