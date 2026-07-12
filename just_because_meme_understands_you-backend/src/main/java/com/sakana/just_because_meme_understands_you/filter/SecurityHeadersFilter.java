package com.sakana.just_because_meme_understands_you.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为所有 HTTP 响应附加安全头，降低点击劫持、MIME 嗅探等常见 Web 风险。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    @Value("${app.security.headers.enabled:true}")
    private boolean headersEnabled;

    @Value("${app.security.headers.hsts-enabled:false}")
    private boolean hstsEnabled;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (headersEnabled) {
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("X-Frame-Options", "DENY");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            response.setHeader("X-XSS-Protection", "0");
            if (hstsEnabled && request.isSecure()) {
                response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }
        }
        filterChain.doFilter(request, response);
    }
}
