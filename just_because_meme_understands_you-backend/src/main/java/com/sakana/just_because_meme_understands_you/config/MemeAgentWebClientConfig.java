package com.sakana.just_because_meme_understands_you.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class MemeAgentWebClientConfig {

    public static final String MEME_AGENT_WEB_CLIENT = "memeAgentWebClient";

    @Bean(name = MEME_AGENT_WEB_CLIENT)
    public WebClient memeAgentWebClient(MemeAgentProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(properties.getAgent().getResponseTimeoutMs()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) Math.min(properties.getAgent().getConnectTimeoutMs(), Integer.MAX_VALUE));

        return WebClient.builder()
                .baseUrl(properties.getAgent().getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
