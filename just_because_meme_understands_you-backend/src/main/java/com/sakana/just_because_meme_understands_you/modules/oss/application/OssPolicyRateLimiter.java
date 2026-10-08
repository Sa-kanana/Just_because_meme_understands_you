package com.sakana.just_because_meme_understands_you.modules.oss.application;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.ratelimit.RateLimit;
import org.springframework.stereotype.Component;

/**
 * OSS 上传凭证接口限流，防止登录用户刷 policy 造成滥用。
 */
@Component
public class OssPolicyRateLimiter {

    @RateLimit(count = 10, interval = 1, timeUnit = RateLimit.TimeUnit.MINUTES,
            dimension = RateLimit.Dimension.USER_IP,
            limitProperty = "oss.policy-rate-limit-per-minute",
            message = "上传凭证请求过于频繁，请稍后重试")
    public void check(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
    }
}
