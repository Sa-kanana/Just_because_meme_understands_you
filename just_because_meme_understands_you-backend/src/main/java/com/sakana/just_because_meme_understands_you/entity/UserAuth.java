package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户认证信息表，对应数据库表 user_auth
 */
@Data
@TableName("user_auth")
public class UserAuth {

    /** 主键，MyBatis-Plus 雪花算法自动填充 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联的用户 id
     */
    private Long userId;

    /**
     * 登录类型（如 email、phone 等），对应 identity_type
     */
    private String identityType;

    /**
     * 标识（如邮箱、手机号），对应 identifier
     */
    private String identifier;

    /**
     * 凭证（加密后的密码），对应数据库字段 password
     */
    @TableField("password")
    private String credential;

    /**
     * 最近一次修改密码时间
     */
    @TableField("password_changed_at")
    private LocalDateTime passwordChangedAt;
}

