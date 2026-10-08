package com.sakana.just_because_meme_understands_you.modules.report.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户对公开梗的举报；同一用户对同一梗只能提交一次。 */
@Data
@TableName("meme_report")
public class MemeReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memeId;
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
