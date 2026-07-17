package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户评论点赞，对应表 user_comment_like。
 * 取消点赞走软删 is_deleted。
 */
@Data
@TableName("user_comment_like")
public class UserCommentLike {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long commentId;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
