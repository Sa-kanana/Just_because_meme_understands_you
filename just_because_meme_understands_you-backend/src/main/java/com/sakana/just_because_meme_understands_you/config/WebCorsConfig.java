package com.sakana.just_because_meme_understands_you.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS 白名单：生产环境通过环境变量配置允许的前端域名，避免任意来源跨域调用。
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.enabled:true}")
    private boolean corsEnabled;

    @Value("${app.cors.allowed-origins:http://localhost,http://localhost:80,http://127.0.0.1}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        if (!corsEnabled) {
            return;
        }
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        if (origins.length == 0) {
            return;
        }
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
