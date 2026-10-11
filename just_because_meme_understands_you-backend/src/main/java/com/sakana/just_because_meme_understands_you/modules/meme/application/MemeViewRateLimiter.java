package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.sakana.just_because_meme_understands_you.common.ratelimit.RateLimit;
import org.springframework.stereotype.Component;
import com.sakana.just_because_meme_understands_you.common.ratelimit.RateLimitAspect;

/**
 * 浏览上报限流，防止刷量。
 */
@Component
public class MemeViewRateLimiter {

    /** 访客键由注解切面处理；此入口放在业务校验之后以保留原调用顺序。 */
    @RateLimit(count = 30, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES,
            dimension = RateLimit.Dimension.CUSTOM,
            limitProperty = "meme.view.report-rate-limit-per-minute",
            key = "#p0",
            condition = "#p0 != null && !#p0.isBlank()",
            message = "浏览上报过于频繁，请稍后重试")
    public void check(String viewerKey) {
        // 限流逻辑由 RateLimitAspect 统一执行。
    }
}
