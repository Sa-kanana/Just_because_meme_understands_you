package com.sakana.just_because_meme_understands_you.modules.auth.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * GitHub OAuth 应用配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.oauth.github")
public class GithubOAuthProperties {

    /** GitHub OAuth App Client ID */
    private String clientId = "";

    /** GitHub OAuth App Client Secret */
    private String clientSecret = "";

    /**
     * GitHub 授权回调地址（须与 GitHub 应用配置一致）。
     * 例：http://localhost:8080/v1/auth/oauth/github/callbacks
     */
    private String redirectUri = "http://localhost:8080/v1/auth/oauth/github/callbacks";

    /**
     * 授权成功后跳转的前端回调页。
     * 例：http://localhost/login/oauth/callback
     */
    private String frontendCallbackUrl = "http://localhost/login/oauth/callback";

    /** 授权 scope */
    private String scope = "read:user user:email";

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && redirectUri != null && !redirectUri.isBlank()
                && frontendCallbackUrl != null && !frontendCallbackUrl.isBlank();
    }
}
