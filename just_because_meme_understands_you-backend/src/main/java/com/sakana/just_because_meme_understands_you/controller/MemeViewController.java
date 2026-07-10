package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.MemeViewReportRequestDTO;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeViewService;
import com.sakana.just_because_meme_understands_you.vo.MemePageViewBatchVO;
import com.sakana.just_because_meme_understands_you.vo.MemePageViewVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MemeViewController {

    @Resource
    private IMemeViewService memeViewService;

    /**
     * 上报浏览（匿名/登录均可）
     * POST /detail/views
     */
    @PostMapping("/detail/views")
    public Result<MemePageViewVO> reportView(@RequestBody MemeViewReportRequestDTO request,
                                             @RequestHeader(value = "X-View-Session-Id", required = false) String viewSessionId,
                                             HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.currentUserId(httpServletRequest);
        return Result.success(memeViewService.reportView(userId, viewSessionId, request, httpServletRequest));
    }

    /**
     * 查询单个梗浏览量
     * GET /views/{memeId}/count
     */
    @GetMapping("/views/{memeId}/count")
    public Result<MemePageViewVO> viewCount(@PathVariable("memeId") String memeId) {
        return Result.success(memeViewService.getViewCount(AuthContext.parseLongId(memeId, "memeId")));
    }

    /**
     * 批量查询浏览量
     * GET /views/counts?memeIds=1,2,3
     */
    @GetMapping("/views/counts")
    public Result<MemePageViewBatchVO> batchViewCounts(@RequestParam(value = "memeIds", required = false) String memeIds) {
        return Result.success(memeViewService.batchViewCounts(memeIds));
    }
}
