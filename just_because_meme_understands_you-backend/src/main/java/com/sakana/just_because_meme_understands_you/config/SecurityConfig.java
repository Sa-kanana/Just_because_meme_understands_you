package com.sakana.just_because_meme_understands_you.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(@NonNull HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 本项目统一由 WebMvc 的 JwtAuthInterceptor 处理登录态校验，避免 Security 抢先返回 403
                        .anyRequest().permitAll()
                )
                // 关闭浏览器默认的 HTTP Basic 弹窗认证，前后端走自定义登录逻辑
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(form -> form.disable())
                // 禁用 Spring Security 默认的 /logout 处理，让自定义 Controller 生效
                .logout(logout -> logout.disable());
        return http.build();
    }
}

