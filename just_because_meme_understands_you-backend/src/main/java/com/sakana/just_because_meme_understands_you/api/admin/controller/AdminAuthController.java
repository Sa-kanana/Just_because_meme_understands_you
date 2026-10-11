package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.sakana.just_because_meme_understands_you.api.user.dto.LoginRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.AuthTokenBundleVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.LoginResponseVO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.auth.application.IAuthService;
import com.sakana.just_because_meme_understands_you.modules.auth.application.LoginRateLimiter;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 使用共享用户账号登录和维护管理端会话。 */
@RestController
@RequestMapping("/admin/v1/auth")
public class AdminAuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    @Resource
    private IAuthService authService;

    @Resource
    private LoginRateLimiter loginRateLimiter;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMillis;

    @Value("${jwt.cookie-secure:false}")
    private boolean cookieSecure;

    /** 管理员登录与普通登录共用账号密码校验，并额外要求管理角色。 */
    @PostMapping("/login")
    public Result<LoginResponseVO> login(@Valid @RequestBody LoginRequestDTO request,
                                         HttpServletResponse response) {
        loginRateLimiter.checkAllowed(request.getEmail());
        try {
            AuthTokenBundleVO tokenBundle = authService.loginAdmin(request);
            loginRateLimiter.onSuccess(request.getEmail());
            writeRefreshTokenCookie(response, tokenBundle.getRefreshToken());
            return Result.success(buildLoginResponse(tokenBundle));
        } catch (BizException exception) {
            if (exception.getCode() == Result.CODE_ERROR && "账号或密码错误".equals(exception.getMessage())) {
                loginRateLimiter.onFailure(request.getEmail());
            }
            throw exception;
        }
    }

    /** 用共享 refresh 会话续签管理端访问令牌。 */
    @PutMapping("/sessions/current")
    public Result<LoginResponseVO> renewLogin(
            @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        AuthTokenBundleVO tokenBundle = authService.renewAdminLogin(refreshToken);
        writeRefreshTokenCookie(response, tokenBundle.getRefreshToken());
        return Result.success(buildLoginResponse(tokenBundle));
    }

    /** 退出共享用户会话并清除 refresh cookie。 */
    @DeleteMapping("/sessions/current")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public Result<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        authService.logout(authorization.substring(7), refreshToken);
        clearRefreshTokenCookie(response);
        return Result.success("退出成功", null);
    }

    private LoginResponseVO buildLoginResponse(AuthTokenBundleVO tokenBundle) {
        LoginResponseVO response = new LoginResponseVO();
        response.setToken(tokenBundle.getAccessToken());
        response.setUser(tokenBundle.getUser());
        return response;
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
