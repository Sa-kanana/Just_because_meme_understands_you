package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.ChangePasswordRequestDTO;
import com.sakana.just_because_meme_understands_you.service.user.IAccountSettingsService;
import com.sakana.just_because_meme_understands_you.vo.AccountSecurityVO;
import com.sakana.just_because_meme_understands_you.vo.AccountSettingsVO;
import com.sakana.just_because_meme_understands_you.vo.ChangePasswordResultVO;
import com.sakana.just_because_meme_understands_you.vo.RevokeSessionsResultVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账号设置：聚合页、安全概览、登录态改密、退出全部设备。
 */
@RestController
@RequestMapping("/user/me")
public class AccountSettingsController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    @Resource
    private IAccountSettingsService accountSettingsService;

    @Value("${jwt.cookie-secure:false}")
    private boolean cookieSecure;

    @GetMapping("/settings")
    public Result<AccountSettingsVO> getSettings(
            @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(accountSettingsService.getSettings(userId, refreshToken));
    }

    @GetMapping("/security")
    public Result<AccountSecurityVO> getSecurity(
            @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(accountSettingsService.getSecurity(userId, refreshToken));
    }

    @PutMapping("/password")
    public Result<ChangePasswordResultVO> changePassword(
            @RequestBody ChangePasswordRequestDTO body,
            HttpServletRequest request,
            HttpServletResponse response) {
        Long userId = AuthContext.requireCurrentUserId(request);
        ChangePasswordResultVO result = accountSettingsService.changePassword(userId, body);
        clearRefreshTokenCookie(response);
        return Result.success(result);
    }

    @PostMapping("/sessions/revoke-all")
    public Result<RevokeSessionsResultVO> revokeAllSessions(
            HttpServletRequest request,
            HttpServletResponse response) {
        Long userId = AuthContext.requireCurrentUserId(request);
        RevokeSessionsResultVO result = accountSettingsService.revokeAllSessions(userId);
        clearRefreshTokenCookie(response);
        return Result.success(result);
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
