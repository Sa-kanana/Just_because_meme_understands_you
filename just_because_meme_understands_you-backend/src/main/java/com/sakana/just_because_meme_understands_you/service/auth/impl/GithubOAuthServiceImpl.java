package com.sakana.just_because_meme_understands_you.service.auth.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.constant.AuthConstants;
import com.sakana.just_because_meme_understands_you.config.GithubOAuthProperties;
import com.sakana.just_because_meme_understands_you.service.auth.IAuthService;
import com.sakana.just_because_meme_understands_you.service.auth.IGithubOAuthService;
import com.sakana.just_because_meme_understands_you.service.auth.client.GithubOAuthClient;
import com.sakana.just_because_meme_understands_you.vo.AuthTokenBundleVO;
import com.sakana.just_because_meme_understands_you.vo.GithubOAuthTicketVO;
import com.sakana.just_because_meme_understands_you.vo.LoginUserVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class GithubOAuthServiceImpl implements IGithubOAuthService {

    private static final Set<String> GUEST_PATHS = Set.of(
            "/login", "/register", "/forgot-password", "/login/oauth/callback");

    @Resource
    private GithubOAuthProperties properties;

    @Resource
    private GithubOAuthClient githubOAuthClient;

    @Resource
    private IAuthService authService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Override
    public String buildAuthorizeUrl(String redirectPath) {
        ensureConfigured();
        String state = UUID.randomUUID().toString().replace("-", "");
        String safeRedirect = sanitizeRedirectPath(redirectPath);
        String stateKey = AuthConstants.OAUTH_GITHUB_STATE_PREFIX + state;
        stringRedisTemplate.opsForValue().set(
                stateKey,
                safeRedirect,
                AuthConstants.OAUTH_STATE_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        return UriComponentsBuilder
                .fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("scope", properties.getScope())
                .queryParam("state", state)
                .queryParam("allow_signup", "true")
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
    }

    @Override
    public GithubOAuthTicketVO handleCallback(String code, String state) {
        ensureConfigured();
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "GitHub 回调参数不完整");
        }
        String stateKey = AuthConstants.OAUTH_GITHUB_STATE_PREFIX + state.trim();
        String redirectPath = stringRedisTemplate.opsForValue().getAndDelete(stateKey);
        if (!StringUtils.hasText(redirectPath)) {
            throw new BizException(Result.CODE_ERROR, "授权已过期，请重新登录");
        }
        String safeRedirect = sanitizeRedirectPath(redirectPath);

        String githubAccessToken = githubOAuthClient.exchangeCodeForAccessToken(code.trim());
        Map<String, String> profile = githubOAuthClient.fetchUserProfile(githubAccessToken);
        AuthTokenBundleVO bundle = authService.loginOrRegisterGithub(
                profile.get("id"),
                profile.get("login"),
                profile.get("name"),
                profile.get("avatarUrl"),
                profile.get("email")
        );

        String ticket = UUID.randomUUID().toString().replace("-", "");
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("accessToken", bundle.getAccessToken());
            payload.put("refreshToken", bundle.getRefreshToken());
            payload.put("redirect", safeRedirect);
            payload.put("user", bundle.getUser());
            stringRedisTemplate.opsForValue().set(
                    AuthConstants.OAUTH_TICKET_PREFIX + ticket,
                    objectMapper.writeValueAsString(payload),
                    AuthConstants.OAUTH_TICKET_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.error("写入 OAuth ticket 失败", e);
            throw new BizException(Result.CODE_ERROR, "登录态签发失败，请重试");
        }
        return new GithubOAuthTicketVO(ticket, safeRedirect);
    }

    @Override
    public AuthTokenBundleVO exchangeTicket(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "缺少登录凭证");
        }
        String key = AuthConstants.OAUTH_TICKET_PREFIX + ticket.trim();
        String raw = stringRedisTemplate.opsForValue().getAndDelete(key);
        if (!StringUtils.hasText(raw)) {
            throw new BizException(Result.CODE_ERROR, "登录凭证无效或已过期，请重新授权");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(raw, Map.class);
            String accessToken = String.valueOf(payload.get("accessToken"));
            String refreshToken = String.valueOf(payload.get("refreshToken"));
            LoginUserVO user = objectMapper.convertValue(payload.get("user"), LoginUserVO.class);
            if (!StringUtils.hasText(accessToken)
                    || !StringUtils.hasText(refreshToken)
                    || "null".equals(accessToken)
                    || "null".equals(refreshToken)
                    || user == null
                    || user.getId() == null) {
                throw new BizException(Result.CODE_ERROR, "登录凭证损坏，请重新授权");
            }
            AuthTokenBundleVO bundle = new AuthTokenBundleVO();
            bundle.setAccessToken(accessToken);
            bundle.setRefreshToken(refreshToken);
            bundle.setUser(user);
            return bundle;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("解析 OAuth ticket 失败", e);
            throw new BizException(Result.CODE_ERROR, "登录凭证无效，请重新授权");
        }
    }

    @Override
    public String buildFrontendSuccessRedirect(String ticket, String redirectPath) {
        return UriComponentsBuilder
                .fromUriString(properties.getFrontendCallbackUrl())
                .queryParam("ticket", ticket)
                .queryParam("redirect", sanitizeRedirectPath(redirectPath))
                .build(true)
                .toUriString();
    }

    @Override
    public String buildFrontendErrorRedirect(String message) {
        String msg = StringUtils.hasText(message) ? message : "GitHub 登录失败";
        return UriComponentsBuilder
                .fromUriString(properties.getFrontendCallbackUrl())
                .queryParam("error", msg)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
    }

    private void ensureConfigured() {
        if (!properties.isConfigured()) {
            throw new BizException(Result.CODE_ERROR, "GitHub 登录未配置");
        }
    }

    private static String sanitizeRedirectPath(String rawPath) {
        String path = rawPath != null ? rawPath.trim() : "";
        if (!path.startsWith("/") || path.startsWith("//")) {
            return "/";
        }
        String pathOnly = path.contains("?") ? path.substring(0, path.indexOf('?')) : path;
        if (GUEST_PATHS.contains(pathOnly)) {
            return "/";
        }
        return path;
    }
}
