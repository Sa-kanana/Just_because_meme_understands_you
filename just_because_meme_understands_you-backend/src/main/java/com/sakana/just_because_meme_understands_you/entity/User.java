package com.sakana.just_because_meme_understands_you.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户基本信息表，对应数据库表 user
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 个性签名
     */
    private String signature;

    /**
     * 性别：0 未知，1 男，2 女
     */
    private Integer gender;

    /**
     * 生日
     */
    private LocalDate birthday;

    /**
     * 角色（ROLE_USER, ROLE_ADMIN）
     */
    private String role;

    /**
     * 状态：0 禁用，1 正常
     */
    private Integer status;

    /**
     * 创建时间，对应 create_time
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间，对应 update_time
     */
    @TableField("update_time")
    private LocalDateTime updateTime;
}

