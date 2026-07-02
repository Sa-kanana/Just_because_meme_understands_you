package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_relation")
public class UserRelation {

    @TableId
    private Long id;

    private Long fromUserId;

    private Long toUserId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
