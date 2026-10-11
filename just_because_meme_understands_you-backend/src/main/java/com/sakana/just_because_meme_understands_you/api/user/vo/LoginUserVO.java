package com.sakana.just_because_meme_understands_you.api.user.vo;

import lombok.Data;

import java.util.List;

/**
 * 登录响应中的用户信息
 */
@Data
public class LoginUserVO {

    private Long id;

    private String nickname;

    private String avatar;

    private String signature;

    /**
     * 角色（ROLE_USER, ROLE_ADMIN）
     */
    private String role;

    /** 规范化角色代码列表：USER、ADMIN、SUPER_ADMIN。 */
    private List<String> roles;

    /**
     * 状态：0 禁用，1 正常
     */
    private Integer status;
}

