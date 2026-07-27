package com.sakana.just_because_meme_understands_you.service.auth;

import com.sakana.just_because_meme_understands_you.vo.CaptchaResponseVO;

/**
 * 图形人机验证码：签发、一次性核销。
 */
public interface ICaptchaService {

    /**
     * 生成新验证码图，答案写入 Redis。
     */
    CaptchaResponseVO create();

    /**
     * 校验并消费验证码（成功或失败后均删除，防止重放）。
     *
     * @param captchaId   签发时的 id
     * @param captchaCode 用户输入
     */
    void verifyAndConsume(String captchaId, String captchaCode);
}
