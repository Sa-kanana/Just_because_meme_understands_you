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
 * <p>内网穿透时 Origin 为公网域名，若不在白名单，Spring 会直接返回 HTTP 403（Invalid CORS request）。
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.enabled:true}")
    private boolean corsEnabled;

    @Value("${app.cors.allowed-origins:http://localhost,http://localhost:80,http://127.0.0.1,http://localhost:5174,http://127.0.0.1:5174}")
    private String allowedOrigins;

    /**
     * 模式匹配（支持 *）。开发内网穿透可配 https://*.cpolar.cn 等。
     */
    @Value("${app.cors.allowed-origin-patterns:}")
    private String allowedOriginPatterns;

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        if (!corsEnabled) {
            return;
        }
        String[] origins = splitCsv(allowedOrigins);
        String[] patterns = splitCsv(allowedOriginPatterns);
        if (origins.length == 0 && patterns.length == 0) {
            return;
        }
        var registration = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
        if (origins.length > 0) {
            registration.allowedOrigins(origins);
        }
        if (patterns.length > 0) {
            registration.allowedOriginPatterns(patterns);
        }
    }

    private static String[] splitCsv(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new String[0];
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }
}
