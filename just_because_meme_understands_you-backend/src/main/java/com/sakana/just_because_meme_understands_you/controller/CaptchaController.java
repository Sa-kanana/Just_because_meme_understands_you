package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.service.auth.ICaptchaService;
import com.sakana.just_because_meme_understands_you.vo.CaptchaResponseVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人机验证码签发（公开接口）。
 */
@RestController
public class CaptchaController {

    @Resource
    private ICaptchaService captchaService;

    /**
     * 获取图形验证码。
     * GET /captcha
     */
    @GetMapping("/captcha")
    public Result<CaptchaResponseVO> createCaptcha() {
        return Result.success(captchaService.create());
    }
}
