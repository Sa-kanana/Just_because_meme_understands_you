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
        //创建 Reactor Netty 的 HttpClient
        HttpClient httpClient = HttpClient.create()
                // 响应超时：默认 10 分钟（600000ms
                .responseTimeout(Duration.ofMillis(properties.getAgent().getResponseTimeoutMs()))
                // 连接超时：默认 2 秒
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) Math.min(properties.getAgent().getConnectTimeoutMs(), Integer.MAX_VALUE));
        //构建 WebClient
        return WebClient.builder()
                // 基础 URL：http://127.0.0.1:8000
                .baseUrl(properties.getAgent().getBaseUrl())
                //将 HttpClient 接入 WebClient
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
