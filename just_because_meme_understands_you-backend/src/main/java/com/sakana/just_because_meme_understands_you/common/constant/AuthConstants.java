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

    // ---- 角色 ----
    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    // ---- JWT claim key ----
    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String CLAIM_LOGIN_TYPE = "loginType";
    public static final String CLAIM_ROLE = "role";
    /** 会话版本号：改密 / 全量踢下线时递增，使历史 access/refresh 立即失效 */
    public static final String CLAIM_TOKEN_VERSION = "tv";

    // ---- token 类型 ----
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    // ---- 登录类型 ----
    public static final String LOGIN_TYPE_EMAIL = "email";
    public static final String LOGIN_TYPE_GITHUB = "github";

    // ---- Redis key 前缀 ----
    public static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";
    /**
     * 兼容旧版：单 refresh 索引。新会话写入会话集合后逐步淘汰。
     */
    public static final String USER_REFRESH_INDEX_PREFIX = "auth:user:refresh:";
    /** 用户多设备 refresh 会话集合（value = refresh md5） */
    public static final String USER_SESSIONS_PREFIX = "auth:user:sessions:";
    /** 用户令牌版本：值越大表示越新的有效代际 */
    public static final String USER_TOKEN_VERSION_PREFIX = "auth:tv:";
    public static final String ACCESS_BLACKLIST_PREFIX = "auth:blacklist:access:";
    public static final String RESET_TOKEN_PREFIX = "reset_token:";
    /** GitHub OAuth state → 登录后回跳路径 */
    public static final String OAUTH_GITHUB_STATE_PREFIX = "auth:oauth:github:state:";
    /** GitHub OAuth 一次性 ticket → 登录包 JSON */
    public static final String OAUTH_TICKET_PREFIX = "auth:oauth:ticket:";

    // ---- 验证码 Redis key 前缀 ----
    public static final String REGISTER_CODE_PREFIX = "register:code:";
    public static final String REGISTER_CODE_RATE_PREFIX = "register:code:rate:";
    public static final String FORGOT_PASSWORD_CODE_PREFIX = "forgot_password:";
    public static final String FORGOT_PASSWORD_RATE_PREFIX = "forgot_password:rate:";
    /** 图形人机验证码 captcha:{id} -> code */
    public static final String CAPTCHA_PREFIX = "auth:captcha:";

    // ---- 验证码时效 ----
    public static final long REGISTER_CODE_TTL_MINUTES = 5L;
    public static final long FORGOT_PASSWORD_CODE_TTL_MINUTES = 5L;
    public static final long RESET_TOKEN_TTL_MINUTES = 10L;
    public static final long CODE_RATE_LIMIT_SECONDS = 60L;
    /** 图形验证码有效期（秒） */
    public static final long CAPTCHA_TTL_SECONDS = 300L;
    /** OAuth state / ticket 有效期 */
    public static final long OAUTH_STATE_TTL_MINUTES = 10L;
    public static final long OAUTH_TICKET_TTL_MINUTES = 2L;

    // ---- 黑名单最小留存时间，避免 access token 即将过期时写入负 TTL ----
    public static final long BLACKLIST_MIN_TTL_MILLIS = 1000L;
}
