package com.sakana.just_because_meme_understands_you.modules.feedback.application;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.ratelimit.RateLimit;
import org.springframework.stereotype.Component;

/**
 * 反馈提交限流：按用户 + IP，默认每分钟 3 次。
 */
@Component
public class FeedbackRateLimiter {

    @RateLimit(count = 3, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES,
            dimension = RateLimit.Dimension.USER_IP,
            limitProperty = "app.feedback.rate-limit-per-minute",
            message = "提交过于频繁，请稍后再试")
    public void check(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "请先登录后再提交反馈");
        }
    }
}
