package com.sakana.just_because_meme_understands_you.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 解析客户端真实 IP。仅在请求来自可信反向代理时才信任 X-Forwarded-For / X-Real-IP，
 * 避免攻击者伪造头绕过限流。
 */
@Component
public class ClientIpResolver {

    @Value("${app.security.trusted-proxies:}")
    private String trustedProxiesConfig;

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddr = normalizeIp(request.getRemoteAddr());
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            int commaIndex = xForwardedFor.indexOf(',');
            String first = commaIndex > 0
                    ? xForwardedFor.substring(0, commaIndex).trim()
                    : xForwardedFor.trim();
            if (StringUtils.hasText(first)) {
                return normalizeIp(first);
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return normalizeIp(realIp.trim());
        }
        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        if (!StringUtils.hasText(trustedProxiesConfig)) {
            return false;
        }
        Set<String> trusted = Arrays.stream(trustedProxiesConfig.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(this::normalizeIp)
                .collect(Collectors.toSet());
        return trusted.contains(remoteAddr);
    }

    private String normalizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "unknown";
        }
        String trimmed = ip.trim();
        if (trimmed.startsWith("::ffff:")) {
            return trimmed.substring(7);
        }
        return trimmed;
    }
}
