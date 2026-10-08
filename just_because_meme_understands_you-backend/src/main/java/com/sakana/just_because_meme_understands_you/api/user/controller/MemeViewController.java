package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeViewReportRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.meme.application.IMemeViewService;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemePageViewBatchVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemePageViewVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

@RestController
@RequestMapping("/v1")
public class MemeViewController {

    @Resource
    private IMemeViewService memeViewService;

    /**
     * 上报浏览（匿名/登录均可）
     * POST /v1/meme-views
     */
    @PostMapping("/meme-views")
    public Result<MemePageViewVO> reportView(@RequestBody MemeViewReportRequestDTO request,
                                             @RequestHeader(value = "X-View-Session-Id", required = false) String viewSessionId,
                                             HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.currentUserId(httpServletRequest);
        return Result.success(memeViewService.reportView(userId, viewSessionId, request, httpServletRequest));
    }

    /**
     * 查询单个梗浏览量
     * GET /v1/memes/{memeId}/views/count
     */
    @GetMapping("/memes/{memeId}/views/count")
    public Result<MemePageViewVO> viewCount(@PathVariable("memeId") String memeId) {
        return Result.success(memeViewService.getViewCount(RequestIdParser.parseLongId(memeId, "memeId")));
    }

    /**
     * 批量查询浏览量
     * GET /v1/meme-view-counts?memeIds=1,2,3
     */
    @GetMapping("/meme-view-counts")
    public Result<MemePageViewBatchVO> batchViewCounts(@RequestParam(value = "memeIds", required = false) String memeIds) {
        return Result.success(memeViewService.batchViewCounts(memeIds));
    }
}
