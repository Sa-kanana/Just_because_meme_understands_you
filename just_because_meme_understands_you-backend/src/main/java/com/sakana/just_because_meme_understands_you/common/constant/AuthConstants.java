package com.sakana.just_because_meme_understands_you.common.constant;

/**
 * 认证相关常量，统一维护 JWT claim key、token 类型、Redis key 前缀等，
 * 消除 AuthServiceImpl 与 JwtAuthInterceptor 之间的重复定义。
 *
 * @author sakana
 */
public final class AuthConstants {

    private AuthConstants() {
    }

    // ---- JWT claim key ----
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String CLAIM_LOGIN_TYPE = "loginType";
    public static final String CLAIM_ROLE = "role";

    // ---- token 类型 ----
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    // ---- 登录类型 ----
    public static final String LOGIN_TYPE_EMAIL = "email";

    // ---- Redis key 前缀 ----
    public static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";
    /** 用户当前有效 refresh token 索引，用于改密后吊销会话 */
    public static final String USER_REFRESH_INDEX_PREFIX = "auth:user:refresh:";
    public static final String ACCESS_BLACKLIST_PREFIX = "auth:blacklist:access:";
    public static final String RESET_TOKEN_PREFIX = "reset_token:";

    // ---- 验证码 Redis key 前缀 ----
    public static final String REGISTER_CODE_PREFIX = "register:code:";
    public static final String REGISTER_CODE_RATE_PREFIX = "register:code:rate:";
    public static final String FORGOT_PASSWORD_CODE_PREFIX = "forgot_password:";
    public static final String FORGOT_PASSWORD_RATE_PREFIX = "forgot_password:rate:";

    // ---- 验证码时效 ----
    public static final long REGISTER_CODE_TTL_MINUTES = 5L;
    public static final long FORGOT_PASSWORD_CODE_TTL_MINUTES = 5L;
    public static final long RESET_TOKEN_TTL_MINUTES = 10L;
    public static final long CODE_RATE_LIMIT_SECONDS = 60L;

    // ---- 黑名单最小留存时间，避免 access token 即将过期时写入负 TTL ----
    public static final long BLACKLIST_MIN_TTL_MILLIS = 1000L;
}
