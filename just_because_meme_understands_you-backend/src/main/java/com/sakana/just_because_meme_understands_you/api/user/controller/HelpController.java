package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.help.application.IHelpService;
import com.sakana.just_because_meme_understands_you.api.user.vo.HelpDocVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帮助中心：公开返回站点帮助文档（Markdown）。
 */
@RestController
@RequestMapping("/v1")
public class HelpController {

    @Resource
    private IHelpService helpService;

    /**
     * 获取帮助文档。
     * GET /help
     */
    @GetMapping("/help")
    public Result<HelpDocVO> getHelpDoc() {
        return Result.success(helpService.getDoc());
    }
}
