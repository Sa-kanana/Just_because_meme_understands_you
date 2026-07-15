package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户关注关系，对应表 user_relation。
 * 单向关注：from_user_id → to_user_id；取消关注走软删 is_deleted。
 */
@Data
@TableName("user_relation")
public class UserRelation {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long fromUserId;

    private Long toUserId;

    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
