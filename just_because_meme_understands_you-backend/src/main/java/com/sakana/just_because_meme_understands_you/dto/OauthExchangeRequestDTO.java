package com.sakana.just_because_meme_understands_you.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * OAuth 一次性 ticket 兑换登录态。
 */
@Data
public class OauthExchangeRequestDTO {

    @NotBlank(message = "缺少登录凭证")
    private String ticket;
}
