package com.sakana.just_because_meme_understands_you.service.auth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.config.GithubOAuthProperties;
import com.sakana.just_because_meme_understands_you.config.GithubOAuthWebClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 调用 GitHub OAuth / User API。
 */
@Slf4j
@Component
public class GithubOAuthClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(12);

    private final WebClient webClient;
    private final GithubOAuthProperties properties;

    public GithubOAuthClient(
            @Qualifier(GithubOAuthWebClientConfig.GITHUB_OAUTH_WEB_CLIENT) WebClient webClient,
            GithubOAuthProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    public String exchangeCodeForAccessToken(String code) {
        try {
            JsonNode node = webClient.post()
                    .uri("https://github.com/login/oauth/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("client_id", properties.getClientId())
                            .with("client_secret", properties.getClientSecret())
                            .with("code", code)
                            .with("redirect_uri", properties.getRedirectUri()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(TIMEOUT);
            if (node == null || !node.hasNonNull("access_token")) {
                String err = node != null && node.has("error_description")
                        ? node.get("error_description").asText()
                        : "GitHub 授权失败";
                throw new BizException(Result.CODE_ERROR, err);
            }
            return node.get("access_token").asText();
        } catch (BizException e) {
            throw e;
        } catch (WebClientResponseException e) {
            log.warn("GitHub token 交换失败: status={}", e.getStatusCode().value());
            throw new BizException(Result.CODE_ERROR, "GitHub 授权失败，请重试");
        } catch (Exception e) {
            log.warn("GitHub token 交换异常", e);
            throw new BizException(Result.CODE_ERROR, "GitHub 授权失败，请重试");
        }
    }

    public Map<String, String> fetchUserProfile(String githubAccessToken) {
        try {
            JsonNode user = webClient.get()
                    .uri("https://api.github.com/user")
                    .headers(h -> h.setBearerAuth(githubAccessToken))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(TIMEOUT);
            if (user == null || !user.hasNonNull("id")) {
                throw new BizException(Result.CODE_ERROR, "无法获取 GitHub 用户信息");
            }

            Map<String, String> profile = new HashMap<>(8);
            profile.put("id", user.get("id").asText());
            profile.put("login", user.path("login").asText(""));
            profile.put("name", user.path("name").asText(""));
            profile.put("avatarUrl", user.path("avatar_url").asText(""));
            String email = user.path("email").asText("");
            if (!StringUtils.hasText(email)) {
                email = fetchPrimaryEmail(githubAccessToken);
            }
            if (StringUtils.hasText(email)) {
                profile.put("email", email.trim().toLowerCase());
            }
            return profile;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("获取 GitHub 用户信息失败", e);
            throw new BizException(Result.CODE_ERROR, "无法获取 GitHub 用户信息");
        }
    }

    private String fetchPrimaryEmail(String githubAccessToken) {
        try {
            JsonNode emails = webClient.get()
                    .uri("https://api.github.com/user/emails")
                    .headers(h -> h.setBearerAuth(githubAccessToken))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(TIMEOUT);
            if (emails == null || !emails.isArray()) {
                return "";
            }
            String fallback = "";
            for (JsonNode item : emails) {
                if (!item.path("verified").asBoolean(false)) {
                    continue;
                }
                String value = item.path("email").asText("");
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                if (item.path("primary").asBoolean(false)) {
                    return value;
                }
                if (!StringUtils.hasText(fallback)) {
                    fallback = value;
                }
            }
            return fallback;
        } catch (Exception e) {
            log.debug("读取 GitHub 邮箱失败（可忽略）: {}", e.getMessage());
            return "";
        }
    }
}
