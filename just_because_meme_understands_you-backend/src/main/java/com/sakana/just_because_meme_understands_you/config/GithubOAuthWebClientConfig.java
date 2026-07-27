package com.sakana.just_because_meme_understands_you.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class GithubOAuthWebClientConfig {

    public static final String GITHUB_OAUTH_WEB_CLIENT = "githubOAuthWebClient";

    @Bean(name = GITHUB_OAUTH_WEB_CLIENT)
    public WebClient githubOAuthWebClient() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "JustBecauseMemeUnderstandsYou")
                .build();
    }
}
