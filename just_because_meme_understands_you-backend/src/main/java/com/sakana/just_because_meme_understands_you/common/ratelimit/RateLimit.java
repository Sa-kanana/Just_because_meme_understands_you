package com.sakana.just_because_meme_understands_you.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口的限流规则，可在同一方法上重复声明多个维度。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimit.Container.class)
public @interface RateLimit {

    /** 窗口内允许的最大请求数。 */
    int count();

    /** 限流窗口长度。 */
    long interval() default 1;

    /** 限流窗口的时间单位。 */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /** 限流维度。 */
    Dimension dimension() default Dimension.GLOBAL;

    /** 从 Spring 配置读取窗口上限；为空时使用 count。 */
    String limitProperty() default "";

    /** SpEL 限流键表达式，可用于按 token、会话或业务用户标识隔离。 */
    String key() default "";

    /** 可选 SpEL 条件；结果为 false 时跳过本条限流规则。 */
    String condition() default "";

    /** 触发限流时返回的提示。 */
    String message() default "请求过于频繁，请稍后再试";

    /** Redis 限流不可用时是否放行；默认失败关闭。 */
    boolean failOpen() default false;

    /** 超限时可调用的降级方法名；留空则抛出 429 业务异常。 */
    String fallback() default "";

    enum TimeUnit {
        MILLISECONDS,
        SECONDS,
        MINUTES,
        HOURS,
        DAYS
    }

    enum Dimension {
        GLOBAL,
        IP,
        USER,
        USER_IP,
        CUSTOM
    }

    /** 用于承载同一方法上的多条限流规则。 */
    @Documented
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Container {
        RateLimit[] value();
    }
}
