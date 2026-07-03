package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("meme_comment")
public class MemeComment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long memeId;

    private Long userId;

    private Long rootId;

    private Long parentId;

    private String content;

    private Integer replyCount;

    private Integer likes;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
