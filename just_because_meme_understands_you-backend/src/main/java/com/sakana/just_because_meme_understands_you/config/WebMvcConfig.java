package com.sakana.just_because_meme_understands_you.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
                        "/user/*/profile",
                        "/user/*/memes",
                        "/user/*/favorites",
                        "/user/*/favorite-folders"
                );
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/login/renew",
                        "/login/refresh",
                        "/register",
                        "/register/send-code",
                        "/password/reset/**",
                        "/user/*/profile",
                        "/user/*/memes",
                        "/user/*/favorites",
                        "/user/*/favorite-folders",
                        "/list",
                        "/search",
                        "/detail",
                        "/detail/*/comments",
                        "/detail/comments/*/replies",
                        "/",
                        "/image",
                        "/error"
                );
        // /user/*/profile 已对外放行用于他人主页查询，这里单独拦截 /user/me/* 需登录的操作
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns(
                        "/user/me/profile",
                        "/user/me/favorite-folders",
                        "/user/me/favorite-folders/**",
                        "/user/me/favorites/**"
                );
    }

}

