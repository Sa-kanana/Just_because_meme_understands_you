package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.service.help.IHelpService;
import com.sakana.just_because_meme_understands_you.vo.HelpDocVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 帮助中心：公开返回站点帮助文档（Markdown）。
 */
@RestController
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
