package com.sakana.just_because_meme_understands_you.service.auth;

import com.sakana.just_because_meme_understands_you.vo.AuthTokenBundleVO;
import com.sakana.just_because_meme_understands_you.vo.GithubOAuthTicketVO;

/**
 * GitHub OAuth 登录。
 */
public interface IGithubOAuthService {

    /**
     * 生成 GitHub 授权跳转地址，并缓存 state → redirect。
     */
    String buildAuthorizeUrl(String redirectPath);

    /**
     * 用授权码完成登录，签发一次性 ticket。
     */
    GithubOAuthTicketVO handleCallback(String code, String state);

    /**
     * 消费 ticket，返回与邮箱登录一致的令牌包。
     */
    AuthTokenBundleVO exchangeTicket(String ticket);

    /**
     * 回调成功后的前端跳转 URL（含 ticket 与 redirect）。
     */
    String buildFrontendSuccessRedirect(String ticket, String redirectPath);

    /**
     * 回调失败时的前端跳转 URL。
     */
    String buildFrontendErrorRedirect(String message);
}
