package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTrendResponseVO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.meme.application.MemeTrendService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 梗热度趋势接口。 */
@RestController
@RequestMapping("/v1")
public class MemeTrendController {

    @Resource
    private MemeTrendService memeTrendService;

    @GetMapping("/trends")
    public Result<MemeTrendResponseVO> trends(
            @RequestParam(value = "days", required = false, defaultValue = "7") Integer days) {
        return Result.success(memeTrendService.getTrends(days == null ? 7 : days));
    }
}
