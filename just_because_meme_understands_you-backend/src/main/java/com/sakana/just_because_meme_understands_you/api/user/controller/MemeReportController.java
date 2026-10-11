package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.api.user.dto.MemeReportRequestDTO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;
import com.sakana.just_because_meme_understands_you.modules.report.application.MemeReportService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 登录用户举报公开梗。 */
@RestController
@RequestMapping("/v1")
public class MemeReportController {
    @Resource private MemeReportService service;

    @PostMapping("/memes/{memeId}/reports")
    public Result<Long> submit(@PathVariable String memeId, @Valid @RequestBody MemeReportRequestDTO body,
                               HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(service.submit(RequestIdParser.parseLongId(memeId, "memeId"), userId,
                body.getReason(), body.getDetail()));
    }

    @PostMapping("/comments/{commentId}/reports")
    public Result<Long> submitComment(@PathVariable String commentId,
                                      @Valid @RequestBody MemeReportRequestDTO body,
                                      HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(service.submitComment(RequestIdParser.parseLongId(commentId, "commentId"), userId,
                body.getReason(), body.getDetail()));
    }
}
