package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.ratelimit.RateLimit;
import com.sakana.just_because_meme_understands_you.api.user.dto.ForgotPasswordSendCodeRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.LoginRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.OauthExchangeRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.RegisterRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.ResetPasswordRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.SendCodeRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.VerifyCodeRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.auth.application.IAuthService;
import com.sakana.just_because_meme_understands_you.modules.auth.application.IGithubOAuthService;
import com.sakana.just_because_meme_understands_you.modules.auth.application.LoginRateLimiter;
import com.sakana.just_because_meme_understands_you.api.user.vo.AuthTokenBundleVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.GithubOAuthTicketVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.LoginResponseVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.RegisterResponseVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.SendCodeResponseVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.VerifyCodeResponseVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证相关接口
 */
@RestController
@RequestMapping("/v1")
public class AuthController {
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    @Resource
    private IAuthService authService;

    @Resource
    private IGithubOAuthService githubOAuthService;

    @Resource
    private LoginRateLimiter loginRateLimiter;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMillis;

    @Value("${jwt.cookie-secure:false}")
    private boolean cookieSecure;

    /**
     * 登录
     * POST /v1/auth/sessions
     */
    @PostMapping("/auth/sessions")
    public Result<LoginResponseVO> login(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletResponse response) {
        loginRateLimiter.checkAllowed(request.getEmail());
        try {
            AuthTokenBundleVO tokenBundle = authService.login(request);
            loginRateLimiter.onSuccess(request.getEmail());
            writeRefreshTokenCookie(response, tokenBundle.getRefreshToken());
            return Result.success(buildLoginResponse(tokenBundle));
        } catch (BizException e) {
            if (e.getCode() == Result.CODE_ERROR && "账号或密码错误".equals(e.getMessage())) {
                loginRateLimiter.onFailure(request.getEmail());
            }
            throw e;
        }
    }

    /**
     * 跳转 GitHub 授权页。
     * GET /v1/auth/oauth/github/authorizations?redirect=/
     */
    @GetMapping("/auth/oauth/github/authorizations")
    public void githubAuthorize(
            @RequestParam(value = "redirect", required = false) String redirect,
            HttpServletResponse response) throws java.io.IOException {
        String authorizeUrl = githubOAuthService.buildAuthorizeUrl(redirect);
        response.sendRedirect(authorizeUrl);
    }

    /**
     * GitHub OAuth 回调：换 ticket 后跳转前端。
     * GET /v1/auth/oauth/github/callbacks
     */
    @GetMapping("/auth/oauth/github/callbacks")
    public void githubCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "error_description", required = false) String errorDescription,
            HttpServletResponse response) throws java.io.IOException {
        try {
            if (StringUtils.hasText(error)) {
                String message = StringUtils.hasText(errorDescription) ? errorDescription : "GitHub 授权已取消";
                response.sendRedirect(githubOAuthService.buildFrontendErrorRedirect(message));
                return;
            }
            GithubOAuthTicketVO ticketVO = githubOAuthService.handleCallback(code, state);
            response.sendRedirect(githubOAuthService.buildFrontendSuccessRedirect(
                    ticketVO.getTicket(), ticketVO.getRedirectPath()));
        } catch (BizException e) {
            response.sendRedirect(githubOAuthService.buildFrontendErrorRedirect(e.getMessage()));
        } catch (Exception e) {
            response.sendRedirect(githubOAuthService.buildFrontendErrorRedirect("GitHub 登录失败，请重试"));
        }
    }

    /**
     * 用一次性 ticket 兑换登录态（与邮箱登录响应一致）。
     * POST /v1/auth/oauth/sessions
     */
    @PostMapping("/auth/oauth/sessions")
    public Result<LoginResponseVO> exchangeOauthTicket(
            @Valid @RequestBody OauthExchangeRequestDTO request,
            HttpServletResponse response) {
        AuthTokenBundleVO tokenBundle = githubOAuthService.exchangeTicket(request.getTicket());
        writeRefreshTokenCookie(response, tokenBundle.getRefreshToken());
        return Result.success(buildLoginResponse(tokenBundle));
    }

    /**
     * 登录自动续航
     * PUT /v1/auth/sessions/current
     */
    @PutMapping("/auth/sessions/current")
    @RateLimit(count = 5, interval = 1, timeUnit = RateLimit.TimeUnit.SECONDS,
            dimension = RateLimit.Dimension.IP,
            limitProperty = "auth.refresh-qps-limit",
            key = "#p0",
            condition = "#p0 != null && !#p0.trim().isEmpty()",
            message = "刷新请求过于频繁，请稍后重试")
    public Result<LoginResponseVO> renewLogin(
            @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        // 无 token 时直接拒绝，且不参与限流。
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        try {
            AuthTokenBundleVO tokenBundle = authService.renewLogin(refreshToken);
            writeRefreshTokenCookie(response, tokenBundle.getRefreshToken());
            return Result.success(buildLoginResponse(tokenBundle));
        } catch (BizException e) {
            if (e.getCode() == Result.CODE_UNAUTHORIZED || e.getCode() == Result.CODE_REFRESH_TOKEN_EXPIRED) {
                clearRefreshTokenCookie(response);
            }
            throw e;
        }
    }

    @PostMapping("/users")
    public Result<RegisterResponseVO> register(@RequestBody RegisterRequestDTO request) {
        RegisterResponseVO responseVO = authService.register(request);
        return Result.success("注册成功", responseVO);
    }

    @PostMapping("/registration-verification-codes")
    public Result<SendCodeResponseVO> sendRegisterCode(@RequestBody SendCodeRequestDTO request) {
        SendCodeResponseVO responseVO = authService.sendRegisterCode(request);
        return Result.success("验证码已发送，请注意查收。", responseVO);
    }

    @PostMapping("/auth/password-reset-requests")
    public Result<SendCodeResponseVO> sendForgotPasswordCode(@RequestBody ForgotPasswordSendCodeRequestDTO request) {
        SendCodeResponseVO responseVO = authService.sendForgotPasswordCode(request.getEmail());
        return Result.success("验证码已发送，请注意查收。", responseVO);
    }

    @PostMapping("/auth/password-reset-verifications")
    public Result<VerifyCodeResponseVO> verifyForgotPasswordCode(@RequestBody VerifyCodeRequestDTO request) {
        VerifyCodeResponseVO responseVO = authService.verifyForgotPasswordCode(request.getEmail(), request.getCode());
        return Result.success(responseVO);
    }

    @PutMapping("/auth/password-resets")
    public Result<Void> resetPassword(@RequestBody ResetPasswordRequestDTO request) {
        authService.resetPassword(request);
        return Result.success("密码重置成功", null);
    }

    @DeleteMapping("/auth/sessions/current")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        String accessToken = extractBearerToken(authorization);
        authService.logout(accessToken, refreshToken);
        clearRefreshTokenCookie(response);
        return Result.success("退出成功", null);
    }

    private String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        return authorization.substring(7);
    }

    private LoginResponseVO buildLoginResponse(AuthTokenBundleVO tokenBundle) {
        LoginResponseVO responseVO = new LoginResponseVO();
        responseVO.setToken(tokenBundle.getAccessToken());
        responseVO.setUser(tokenBundle.getUser());
        return responseVO;
    }

    private void writeRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        long maxAgeSeconds = Math.max(1L, refreshExpirationMillis / 1000L);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

}
