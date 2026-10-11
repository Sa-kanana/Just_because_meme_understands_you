package com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.projection;

import lombok.Data;

/** 用户角色关联查询结果，用于管理端批量装配用户角色。 */
@Data
public class UserRoleAssignment {

    private Long userId;

    private String roleCode;
}
