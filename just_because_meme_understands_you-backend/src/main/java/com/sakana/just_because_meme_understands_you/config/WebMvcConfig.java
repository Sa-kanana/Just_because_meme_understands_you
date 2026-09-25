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
                        "/user/*/favorite-folders",
                        "/user/*/following",
                        "/user/*/followers",
                        "/detail",
                        "/detail/views",
                        "/detail/*/comments",
                        "/detail/comments/*/replies",
                        "/detail/comments/*/anchor",
                        "/detail/comments/*/likes/status",
                        "/list"
                ).order(1);
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/login/renew",
                        "/login/refresh",
                        "/login/oauth/**",
                        "/captcha",
                        "/register",
                        "/register/send-code",
                        "/password/reset/**",
                        "/user/*/profile",
                        "/user/*/memes",
                        "/user/*/favorites",
                        "/user/*/favorite-folders",
                        "/user/*/following",
                        "/user/*/followers",
                        "/list",
                        "/search",
                        "/home/**",
                        "/help",
                        "/detail",
                        "/detail/views",
                        "/views/**",
                        "/detail/*/comments",
                        "/detail/comments/*/replies",
                        "/detail/comments/*/anchor",
                        "/detail/comments/*/likes/status",
                        "/",
                        "/image",
                        "/error"
                ).order(2);
        // /user/*/profile 已对外放行用于他人主页查询，这里单独拦截 /user/me/* 需登录的操作
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns(
                        "/user/me/**"
                ).order(3);
    }

}

