package com.sakana.just_because_meme_understands_you.modules.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 由发起用户管理的静音或屏蔽关系。 */
@Data
@TableName("user_suppression")
public class UserSuppression {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private Long targetUserId;
    private String type;
    private LocalDateTime createdAt;
}
