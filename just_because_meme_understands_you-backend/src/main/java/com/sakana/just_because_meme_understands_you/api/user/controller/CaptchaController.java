package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.auth.application.ICaptchaService;
import com.sakana.just_because_meme_understands_you.api.user.vo.CaptchaResponseVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 人机验证码签发（公开接口）。
 */
@RestController
@RequestMapping("/v1")
public class CaptchaController {

    @Resource
    private ICaptchaService captchaService;

    /**
     * 获取图形验证码。
     * GET /v1/captchas
     */
    @GetMapping("/captchas")
    public Result<CaptchaResponseVO> createCaptcha() {
        return Result.success(captchaService.create());
    }
}
