package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.service.auth.IAuthService;
import com.sakana.just_because_meme_understands_you.service.auth.LoginRateLimiter;
import com.sakana.just_because_meme_understands_you.util.ClientIpResolver;
import com.sakana.just_because_meme_understands_you.util.DigestUtil;
import com.sakana.just_because_meme_understands_you.dto.ForgotPasswordSendCodeRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.LoginRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.AuthTokenBundleVO;
import com.sakana.just_because_meme_understands_you.vo.LoginResponseVO;
import com.sakana.just_because_meme_understands_you.dto.RegisterRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.RegisterResponseVO;
import com.sakana.just_because_meme_understands_you.dto.ResetPasswordRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.SendCodeRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.SendCodeResponseVO;
import com.sakana.just_because_meme_understands_you.dto.VerifyCodeRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.VerifyCodeResponseVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证相关接口
 */
@RestController
public class AuthController {
    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String REFRESH_QPS_KEY_PREFIX = "rate:login:refresh:qps:";
    private static final long REFRESH_QPS_WINDOW_SECONDS = 1L;

    @Resource
    private IAuthService authService;

    @Resource
    private LoginRateLimiter loginRateLimiter;

    @Resource
    private ClientIpResolver clientIpResolver;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMillis;

    @Value("${jwt.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${auth.refresh-qps-limit:5}")
    private int refreshQpsLimit;

    /**
     * 登录
     * POST /login
     * 请求体：{ "email": "...", "password": "...", "loginType": "email" }
     * 响应：Result<DataResponse>，其中 data 包含 token 和 user 信息
     */
    @PostMapping("/login")
    public Result<LoginResponseVO> login(
            @Valid @RequestBody LoginRequestDTO request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        loginRateLimiter.checkAllowed(httpRequest, request.getEmail());
        try {
            AuthTokenBundleVO tokenBundle = authService.login(request);
            loginRateLimiter.onSuccess(request.getEmail());
            writeRefreshTokenCookie(response, tokenBundle.getRefreshToken());
            LoginResponseVO responseVO = buildLoginResponse(tokenBundle);
            return Result.success(responseVO);
        } catch (BizException e) {
            if (e.getCode() == Result.CODE_ERROR && "账号或密码错误".equals(e.getMessage())) {
                loginRateLimiter.onFailure(request.getEmail());
            }
            throw e;
        }
    }

    /**
     * 登录自动续航
     * POST /login/renew（兼容 /login/refresh）
     * refreshToken 从 HttpOnly Cookie 中读取。
     */
    @PostMapping({"/login/renew", "/login/refresh"})
    public Result<LoginResponseVO> renewLogin(
            @CookieValue(value = REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        checkRefreshQpsLimit(request, refreshToken);
        try {
            AuthTokenBundleVO tokenBundle = authService.renewLogin(refreshToken);
            writeRefreshTokenCookie(response, tokenBundle.getRefreshToken());
            LoginResponseVO responseVO = buildLoginResponse(tokenBundle);
            return Result.success(responseVO);
        } catch (BizException e) {
            if (e.getCode() == Result.CODE_UNAUTHORIZED || e.getCode() == Result.CODE_REFRESH_TOKEN_EXPIRED) {
                clearRefreshTokenCookie(response);
            }
            throw e;
        }
    }

    /**
     * 注册
     * POST /register
     * 请求体：按 Apifox 文档中的 email、password、confirmPassword、verificationCode、nickname
     */
    @PostMapping("/register")
    public Result<RegisterResponseVO> register(@RequestBody RegisterRequestDTO request) {
        RegisterResponseVO responseVO = authService.register(request);
        return Result.success("注册成功", responseVO);
    }

    /**
     * 发送注册验证码
     * POST /register/send-code
     * 请求体：{ "email": "xxx@xxx.com" }
     */
    @PostMapping("/register/send-code")
    public Result<SendCodeResponseVO> sendRegisterCode(@RequestBody SendCodeRequestDTO request) {
        SendCodeResponseVO responseVO = authService.sendRegisterCode(request.getEmail());
        // 首次发送或被限频时都返回业务成功，失败情况通过异常处理器统一接管
        // 此处按接口文档要求返回自定义提示文案
        return Result.success("验证码已发送，请注意查收。", responseVO);
    }

    // ---------- 忘记密码三步 ----------

    /**
     * 第一步：请求重置（发邮件）
     * POST /password/reset/send-code
     * 请求体：{ "email": "xxx@xxx.com" }
     */
    @PostMapping("/password/reset/request")
    public Result<SendCodeResponseVO> sendForgotPasswordCode(@RequestBody ForgotPasswordSendCodeRequestDTO request) {
        SendCodeResponseVO responseVO = authService.sendForgotPasswordCode(request.getEmail());
        return Result.success("验证码已发送，请注意查收。", responseVO);
    }

    /**
     * 第二步：验证验证码，获取重置令牌
     * POST /password/reset/verify-code
     * 请求体：{ "email": "xxx@xxx.com", "code": "123456" }
     * 响应 data.token 供第三步使用
     */
    @PostMapping("/password/reset/verify")
    public Result<VerifyCodeResponseVO> verifyForgotPasswordCode(@RequestBody VerifyCodeRequestDTO request) {
        VerifyCodeResponseVO responseVO = authService.verifyForgotPasswordCode(request.getEmail(), request.getCode());
        return Result.success(responseVO);
    }

    /**
     * 第三步：执行重置
     * POST /password/reset/confirm
     * 请求体：{ "token": "temp-uuid-xxxx", "newPassword": "..." }
     */
    @PostMapping("/password/reset/confirm")
    public Result<Void> resetPassword(@RequestBody ResetPasswordRequestDTO request) {
        authService.resetPassword(request);
        return Result.success("密码重置成功",null);
    }

    /**
     * 退出登录
     * POST /logout，需携带有效的 Authorization: Bearer &lt;token&gt;
     * refreshToken 从 Cookie 删除，accessToken 写入 Redis 黑名单。
     */
    @PostMapping("/logout")
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

    private void checkRefreshQpsLimit(HttpServletRequest request, String refreshToken) {
        String clientIp = clientIpResolver.resolve(request);
        String tokenFingerprint = StringUtils.hasText(refreshToken)
                ? DigestUtil.md5Hex(refreshToken)
                : "no-token";
        String rateKey = REFRESH_QPS_KEY_PREFIX + clientIp + ":" + tokenFingerprint;
        Long count = stringRedisTemplate.opsForValue().increment(rateKey);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(rateKey, REFRESH_QPS_WINDOW_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        }
        if (count != null && count > refreshQpsLimit) {
            throw new BizException(Result.CODE_TOO_MANY_REQUESTS, "刷新请求过于频繁，请稍后重试");
        }
    }

}

