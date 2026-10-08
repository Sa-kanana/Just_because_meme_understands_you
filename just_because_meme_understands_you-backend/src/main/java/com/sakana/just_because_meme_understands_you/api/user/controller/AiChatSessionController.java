package com.sakana.just_because_meme_understands_you.api.user.controller;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiChatSessionService;
import com.sakana.just_because_meme_understands_you.api.user.vo.AiChatMessagePageVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.AiChatSessionPageVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

/**
 * AI 搜索会话（MySQL SSOT，非流式 Result）。
 */
@RestController
@RequestMapping("/v1/ai/sessions")
public class AiChatSessionController {

    @Resource
    private IAiChatSessionService aiChatSessionService;

    /**
     * 会话列表
     * GET /ai/sessions?page=&size=
     */
    @GetMapping
    public Result<AiChatSessionPageVO> pageSessions(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(aiChatSessionService.pageSessions(userId, page, size));
    }

    /**
     * 会话消息分页
     * GET /ai/sessions/{sessionId}/messages?page=&size=
     */
    @GetMapping("/{sessionId}/messages")
    public Result<AiChatMessagePageVO> pageMessages(
            @PathVariable("sessionId") String sessionId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(aiChatSessionService.pageMessages(
                userId,
                RequestIdParser.parseLongId(sessionId, "sessionId"),
                page,
                size));
    }

    /**
     * 软删会话
     * DELETE /ai/sessions/{sessionId}
     */
    @DeleteMapping("/{sessionId}")
    public Result<Void> deleteSession(@PathVariable("sessionId") String sessionId,
                                      HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        aiChatSessionService.softDeleteSession(userId, RequestIdParser.parseLongId(sessionId, "sessionId"));
        return Result.success();
    }
}
