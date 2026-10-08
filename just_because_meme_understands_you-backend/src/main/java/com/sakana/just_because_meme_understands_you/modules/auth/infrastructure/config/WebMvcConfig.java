package com.sakana.just_because_meme_understands_you.modules.auth.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.sakana.just_because_meme_understands_you.modules.auth.infrastructure.config.JwtAuthInterceptor;
import com.sakana.just_because_meme_understands_you.modules.auth.infrastructure.config.OptionalJwtAuthInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final OptionalJwtAuthInterceptor optionalJwtAuthInterceptor;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor,
                        OptionalJwtAuthInterceptor optionalJwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
        this.optionalJwtAuthInterceptor = optionalJwtAuthInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(optionalJwtAuthInterceptor)
                .addPathPatterns(
                        "/v1/users/*",
                        "/v1/users/*/memes",
                        "/v1/users/*/favorites",
                        "/v1/users/*/favorite-folders",
                        "/v1/users/*/following",
                        "/v1/users/*/followers",
                        "/v1/memes",
                        "/v1/memes/*",
                        "/v1/memes/*/comments",
                        "/v1/memes/*/views/count",
                        "/v1/meme-searches",
                        "/v1/meme-views",
                        "/v1/meme-view-counts",
                        "/v1/comments/*/replies",
                        "/v1/comments/*/context",
                        "/v1/comments/*/likes/me",
                        "/v1/home/**",
                        "/v1/tags",
                        "/v1/trends",
                        "/v1/help"
                ).order(1);
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/v1/auth/sessions",
                        "/v1/auth/oauth/**",
                        "/v1/captchas",
                        "/v1/users",
                        "/v1/registration-verification-codes",
                        "/v1/auth/password-reset-**",
                        "/v1/users/*",
                        "/v1/users/*/memes",
                        "/v1/users/*/favorites",
                        "/v1/users/*/favorite-folders",
                        "/v1/users/*/following",
                        "/v1/users/*/followers",
                        "/v1/memes",
                        "/v1/memes/*",
                        "/v1/memes/*/comments",
                        "/v1/memes/*/views/count",
                        "/v1/meme-searches",
                        "/v1/meme-views",
                        "/v1/meme-view-counts",
                        "/v1/comments/*/replies",
                        "/v1/comments/*/context",
                        "/v1/comments/*/likes/me",
                        "/v1/home/**",
                        "/v1/tags",
                        "/v1/trends",
                        "/v1/help",
                        "/",
                        "/error"
                ).order(2);
        // 公开的用户资料路由之外，当前用户的资料、设置和安全操作都要求登录。
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns(
                        "/v1/users/me",
                        "/v1/users/me/**"
                ).order(3);
    }

}
