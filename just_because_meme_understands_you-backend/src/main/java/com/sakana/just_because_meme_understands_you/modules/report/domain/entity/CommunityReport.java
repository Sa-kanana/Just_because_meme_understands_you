package com.sakana.just_because_meme_understands_you.modules.report.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 梗与评论共用的社区内容举报记录。 */
@Data
@TableName("community_report")
public class CommunityReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String targetType;
    private Long targetId;
    private Long memeId;
    private Long commentId;
    private Long reporterUserId;
    private String reason;
    private String detail;
    private String status;
    private String result;
    private Long handledBy;
    private String handlingNote;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
