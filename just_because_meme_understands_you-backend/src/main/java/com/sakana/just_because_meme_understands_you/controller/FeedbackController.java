package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.FeedbackSubmitRequestDTO;
import com.sakana.just_because_meme_understands_you.service.feedback.FeedbackRateLimiter;
import com.sakana.just_because_meme_understands_you.service.feedback.IFeedbackService;
import com.sakana.just_because_meme_understands_you.util.ClientIpResolver;
import com.sakana.just_because_meme_understands_you.vo.FeedbackMetaVO;
import com.sakana.just_because_meme_understands_you.vo.FeedbackSubmitVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户反馈：获取类型元数据、提交反馈（邮件通知运营）。
 */
@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    @Resource
    private IFeedbackService feedbackService;

    @Resource
    private FeedbackRateLimiter feedbackRateLimiter;

    @Resource
    private ClientIpResolver clientIpResolver;

    /**
     * 反馈页元数据（类型选项）。
     * GET /feedback/meta
     */
    @GetMapping("/meta")
    public Result<FeedbackMetaVO> getMeta(HttpServletRequest request) {
        AuthContext.requireCurrentUserId(request);
        return Result.success(feedbackService.getMeta());
    }

    /**
     * 提交反馈。
     * POST /feedback
     */
    @PostMapping
    public Result<FeedbackSubmitVO> submit(
            @Valid @RequestBody FeedbackSubmitRequestDTO body,
            HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        feedbackRateLimiter.check(userId, request);
        String clientIp = clientIpResolver.resolve(request);
        return Result.success(feedbackService.submit(userId, body, clientIp));
    }
}
