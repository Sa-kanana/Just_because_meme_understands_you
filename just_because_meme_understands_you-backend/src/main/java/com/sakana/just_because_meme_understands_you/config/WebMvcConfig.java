package com.sakana.just_because_meme_understands_you.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Objects;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    public WebMvcConfig(JwtAuthInterceptor jwtAuthInterceptor) {
        this.jwtAuthInterceptor = jwtAuthInterceptor;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(Objects.requireNonNull(jwtAuthInterceptor, "jwtAuthInterceptor"))
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
                        "/list",
                        "/search",
                        "/detail",
                        "/",
                        "/image",
                        "/error"
                );
        // /user/*/profile 已对外放行用于他人主页查询，这里单独拦截 /user/me/profile 保障“编辑资料回显”必须登录
        registry.addInterceptor(Objects.requireNonNull(jwtAuthInterceptor, "jwtAuthInterceptor"))
                .addPathPatterns("/user/me/profile");
    }

}

