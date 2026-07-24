package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.service.ai.IAiIngestService;
import com.sakana.just_because_meme_understands_you.service.meme.MemeApproveService;
import com.sakana.just_because_meme_understands_you.vo.AiIngestBackfillVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 向量灌库运维入口（需登录；回填受 meme.ai.backfill-enabled 控制）。
 */
@RestController
@RequestMapping("/ai/ingest")
public class AiIngestController {

    @Resource
    private IAiIngestService aiIngestService;

    @Resource
    private MemeApproveService memeApproveService;

    /**
     * 回填已发布梗到 pgvector。
     * POST /ai/ingest/backfill?limit=500
     */
    @PostMapping("/backfill")
    public Result<AiIngestBackfillVO> backfill(@RequestParam(required = false) Integer limit,
                                               HttpServletRequest request) {
        AuthContext.requireCurrentUserId(request);
        return Result.success(aiIngestService.backfillPublished(limit));
    }

    /**
     * 同步单条梗（status=1 灌库，否则删向量）。
     * POST /ai/ingest/sync/{memeId}
     */
    @PostMapping("/sync/{memeId}")
    public Result<Map<String, Object>> syncOne(@PathVariable String memeId,
                                               HttpServletRequest request) {
        AuthContext.requireCurrentUserId(request);
        Long id = AuthContext.parseLongId(memeId, "memeId");
        boolean queued = aiIngestService.syncMeme(id);
        return Result.success(Map.of(
                "memeId", String.valueOf(id),
                "queued", queued
        ));
    }

    /**
     * 审核通过并灌库（status → 1）。
     * POST /ai/ingest/approve/{memeId}
     */
    @PostMapping("/approve/{memeId}")
    public Result<Map<String, Object>> approve(@PathVariable String memeId,
                                               HttpServletRequest request) {
        AuthContext.requireCurrentUserId(request);
        Long id = AuthContext.parseLongId(memeId, "memeId");
        memeApproveService.approveToPublished(id);
        return Result.success(Map.of(
                "memeId", String.valueOf(id),
                "status", 1,
                "statusDesc", "正常"
        ));
    }
}
