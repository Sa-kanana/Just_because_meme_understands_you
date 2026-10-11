package com.sakana.just_because_meme_understands_you.modules.user.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户与角色关联，对应数据库表 user_role。 */
@Data
@TableName("user_role")
public class UserRole {

    /** 关联用户主键；联合主键的另一列为 role_id。 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    @TableField("role_id")
    private Long roleId;

    @TableField("create_time")
    private LocalDateTime createTime;
}
