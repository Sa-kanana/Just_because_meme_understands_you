package com.sakana.just_because_meme_understands_you.modules.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_chat_message")
public class AiChatMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private String role;

    /** 中文说明：消息状态为 COMPLETED、STOPPED 或 FAILED。 */
    private String status;

    private String content;

    private String requestId;

    private Integer tokenEstimate;

    @TableField("create_time")
    private LocalDateTime createTime;
}
