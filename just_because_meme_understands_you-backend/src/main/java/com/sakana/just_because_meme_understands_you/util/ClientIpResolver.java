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


    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        //获取 TCP 连接层的 IP
        String remoteAddr = normalizeIp(request.getRemoteAddr());

        return remoteAddr;
    }

    //标准化ip地址
    private String normalizeIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return "unknown";
        }
        // 1. 处理 IPv4 映射的 IPv6 地址 (如 ::ffff:192.168.1.1 -> 192.168.1.1)
        String trimmed = ip.trim();
        if (trimmed.startsWith("::ffff:")) {
            return trimmed.substring(7);
        }

        // 2. 将本地 IPv6 环回地址统一转为 IPv4 环回地址 (可选，利于本地测试和排查)
        if ("0:0:0:0:0:0:0:1".equals(trimmed) || "::1".equals(trimmed)) {
            return "127.0.0.1";
        }
        return trimmed;
    }
}
