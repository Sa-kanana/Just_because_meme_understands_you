package com.sakana.just_because_meme_understands_you.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationMillis;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT
     *
     * @param subject 主题，一般为用户 id
     * @param claims  额外携带的声明，如角色等
     */
    public String generateToken(@NonNull String subject, @NonNull Map<String, Object> claims) {
        return generateToken(subject, claims, expirationMillis);
    }

    /**
     * 按指定过期时间生成 JWT。
     */
    public String generateToken(@NonNull String subject, @NonNull Map<String, Object> claims, long customExpirationMillis) {
        Instant now = Instant.now();
        Instant expireAt = now.plusMillis(customExpirationMillis);
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expireAt))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析并验证 JWT，验证失败时抛异常
     */
    public Claims parseToken(@NonNull String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }
}

