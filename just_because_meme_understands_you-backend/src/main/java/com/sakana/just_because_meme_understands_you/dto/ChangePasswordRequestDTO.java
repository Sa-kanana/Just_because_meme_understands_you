package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

/**
 * 登录态修改密码请求。
 */
@Data
public class ChangePasswordRequestDTO {

    private String oldPassword;

    private String newPassword;

    private String confirmPassword;
}
