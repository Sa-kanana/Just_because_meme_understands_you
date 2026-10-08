package com.sakana.just_because_meme_understands_you.common.ratelimit;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.common.util.ClientIpResolver;
import com.sakana.just_because_meme_understands_you.common.util.DigestUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 限流 AOP 切面
 * 支持可重复注解，同一方法上的多条限流规则在一次 Lua 调用中原子检查和扣减
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RedissonClient redissonClient;
    private final Environment environment;
    private final ClientIpResolver clientIpResolver;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    private static final String LUA_SCRIPT;
    private volatile String luaScriptSha;
    private volatile RScript rScript;

    static {
        try {
            ClassPathResource resource = new ClassPathResource("scripts/rate_limit_single.lua");
            LUA_SCRIPT = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载限流 Lua 脚本失败", e);
        }
    }

    private synchronized void loadScript() {
        if (rScript == null) {
            rScript = redissonClient.getScript(StringCodec.INSTANCE);
        }
        this.luaScriptSha = rScript.scriptLoad(LUA_SCRIPT);
        log.info("限流 Lua 脚本加载完成, SHA1: {}", luaScriptSha);
    }

    private void ensureScriptLoaded() {
        if (luaScriptSha == null) {
            loadScript();
        }
    }

    /**
     * 环绕通知：拦截带 @RateLimit 或 @RateLimit.Container 注解的方法
     */
    @Around("@annotation(com.sakana.just_because_meme_understands_you.common.ratelimit.RateLimit) || " +
            "@annotation(com.sakana.just_because_meme_understands_you.common.ratelimit.RateLimit.Container)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = method.getDeclaringClass().getSimpleName();
        String methodName = method.getName();

        RateLimit[] rules = method.getAnnotationsByType(RateLimit.class);
        long nowMs = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        List<Object> keysList = new ArrayList<>(rules.length);
        List<Object> args = new ArrayList<>(3 + rules.length * 3);
        List<RateLimitContext> contexts = new ArrayList<>(rules.length);

        for (RateLimit rule : rules) {
            if (!matchesCondition(rule.condition(), joinPoint, method)) {
                continue;
            }
            int limit = resolveLimit(rule);
            long intervalMs = calculateIntervalMs(rule.interval(), rule.timeUnit());
            String key = generateKey(className, methodName, rule, joinPoint, method);

            keysList.add(key);
            contexts.add(new RateLimitContext(rule, key, limit));
        }

        if (contexts.isEmpty()) {
            return joinPoint.proceed();
        }

        args.add(String.valueOf(nowMs));
        args.add(requestId);
        args.add(String.valueOf(contexts.size()));
        for (RateLimitContext context : contexts) {
            args.add(String.valueOf(1));
            args.add(String.valueOf(calculateIntervalMs(context.rule().interval(), context.rule().timeUnit())));
            args.add(String.valueOf(context.limit()));
        }

        Long result;
        try {
            result = executeRateLimitScript(keysList, args.toArray());
        } catch (Exception e) {
            if (contexts.stream().allMatch(context -> context.rule().failOpen())) {
                log.warn("限流检查失败，按配置放行: method={}, err={}", methodName, e.toString());
                return joinPoint.proceed();
            }
            throw e;
        }
        if (result == null || result <= 0) {
            RateLimitContext failedContext = resolveFailedContext(result, contexts);
            return handleRateLimitExceeded(joinPoint, failedContext);
        }

        return joinPoint.proceed();
    }

    private boolean matchesCondition(String condition, ProceedingJoinPoint joinPoint, Method method) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
        Boolean matches = expressionParser.parseExpression(condition).getValue(context, Boolean.class);
        return Boolean.TRUE.equals(matches);
    }

    private int resolveLimit(RateLimit rule) {
        int limit = rule.limitProperty().isBlank()
                ? rule.count()
                : environment.getProperty(rule.limitProperty(), Integer.class, rule.count());
        if (limit < 1) {
            throw new IllegalStateException("限流上限必须大于 0: " + rule.limitProperty());
        }
        return limit;
    }

    private Long executeRateLimitScript(List<Object> keysList, Object[] args) {
        try {
            ensureScriptLoaded();
            Object resultObj = rScript.evalSha(
                    RScript.Mode.READ_WRITE,
                    luaScriptSha,
                    RScript.ReturnType.VALUE,
                    keysList,
                    args
            );
            return convertToLong(resultObj);
        } catch (org.redisson.client.RedisException e) {
            // Redis 重启后脚本缓存丢失，重新加载并重试
            if (e.getMessage() != null && e.getMessage().contains("NOSCRIPT")) {
                loadScript();
                Object resultObj = rScript.evalSha(
                        RScript.Mode.READ_WRITE,
                        luaScriptSha,
                        RScript.ReturnType.VALUE,
                        keysList,
                        args
                );
                return convertToLong(resultObj);
            }
            throw e;
        }
    }

    private RateLimitContext resolveFailedContext(Long result, List<RateLimitContext> contexts) {
        if (result != null && result < 0) {
            int failedIndex = (int) Math.min(Math.abs(result) - 1, contexts.size() - 1L);
            if (failedIndex >= 0) {
                return contexts.get(failedIndex);
            }
        }
        return contexts.get(0);
    }

    private long calculateIntervalMs(long interval, RateLimit.TimeUnit unit) {
        return switch (unit) {
            case MILLISECONDS -> interval;
            case SECONDS -> interval * 1000;
            case MINUTES -> interval * 60 * 1000;
            case HOURS -> interval * 3600 * 1000;
            case DAYS -> interval * 86400 * 1000;
        };
    }

    private Long convertToLong(Object obj) {
        if (obj instanceof Number n) {
            return n.longValue();
        }
        if (obj instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                log.warn("无法将字符串转换为Long: {}", obj);
                return null;
            }
        }
        log.warn("不支持的对象类型转换为Long: {}", obj != null ? obj.getClass().getName() : "null");
        return null;
    }

    private String generateKey(String className, String methodName, RateLimit rule,
                               ProceedingJoinPoint joinPoint, Method method) {
        String hashTag = "{" + className + ":" + methodName + "}";
        String keyPrefix = "ratelimit:" + hashTag;
        String suffix = resolveKeySuffix(rule.key(), joinPoint, method);
        HttpServletRequest request = getCurrentRequest();
        String ip = clientIpResolver.resolve(request);
        Long userId = request == null ? null : AuthContext.currentUserId(request);

        return switch (rule.dimension()) {
            case GLOBAL -> keyPrefix + ":global";
            case IP -> keyPrefix + ":ip:" + ip + suffix;
            case USER -> keyPrefix + ":user:" + (userId == null ? "anonymous" : userId) + suffix;
            case USER_IP -> keyPrefix + ":user-ip:" + (userId == null ? "anonymous" : userId)
                    + ":" + ip + suffix;
            case CUSTOM -> {
                if (suffix.isEmpty()) {
                    throw new IllegalStateException("CUSTOM 限流维度必须配置 key 表达式");
                }
                yield keyPrefix + ":custom:" + suffix.substring(1);
            }
        };
    }

    private String resolveKeySuffix(String keyExpression, ProceedingJoinPoint joinPoint, Method method) {
        if (keyExpression == null || keyExpression.isBlank()) {
            return "";
        }
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(), method, joinPoint.getArgs(), parameterNameDiscoverer);
        Object key = expressionParser.parseExpression(keyExpression).getValue(context);
        String rawKey = key == null ? "null" : String.valueOf(key);
        return ":" + DigestUtil.md5Hex(rawKey);
    }

    private Object handleRateLimitExceeded(ProceedingJoinPoint joinPoint, RateLimitContext context)
            throws Throwable {
        RateLimit rateLimit = context.rule();
        String methodName = joinPoint.getSignature().getName();

        if (rateLimit.fallback() != null && !rateLimit.fallback().isEmpty()) {
            try {
                Method fallbackMethod = findFallbackMethod(joinPoint, rateLimit.fallback());
                if (fallbackMethod != null) {
                    log.debug("限流触发，执行降级方法: {}.{} -> {}",
                            joinPoint.getTarget().getClass().getSimpleName(),
                            methodName,
                            rateLimit.fallback());
                    if (fallbackMethod.getParameterCount() > 0) {
                        return fallbackMethod.invoke(joinPoint.getTarget(), joinPoint.getArgs());
                    } else {
                        return fallbackMethod.invoke(joinPoint.getTarget());
                    }
                }
            } catch (Exception e) {
                log.error("降级方法执行失败: {}", rateLimit.fallback(), e);
            }
        }

        log.debug("限流触发，拒绝请求: key={}, count={} per {} {}",
                context.key(), context.limit(), rateLimit.interval(), rateLimit.timeUnit());
        throw new BizException(Result.CODE_TOO_MANY_REQUESTS, rateLimit.message());
    }

    private Method findFallbackMethod(ProceedingJoinPoint joinPoint, String fallbackName) {
        Class<?> targetClass = joinPoint.getTarget().getClass();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?>[] parameterTypes = signature.getParameterTypes();

        try {
            Method method = targetClass.getDeclaredMethod(fallbackName, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            try {
                Method method = targetClass.getDeclaredMethod(fallbackName);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ex) {
                log.warn("未找到降级方法: {}.{} (需无参或参数列表一致)",
                        targetClass.getSimpleName(), fallbackName);
                return null;
            }
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }

    private record RateLimitContext(RateLimit rule, String key, int limit) {
    }
}
