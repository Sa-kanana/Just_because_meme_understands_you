package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.ai.application.IAiIngestService;
import com.sakana.just_because_meme_understands_you.modules.meme.application.MemeApproveService;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AiIngestBackfillVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Map;
import com.sakana.just_because_meme_understands_you.api.admin.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

/**
 * AI 向量灌库运维入口（管理员；回填受 meme.ai.backfill-enabled 控制）。
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequestMapping("/admin/v1/ai/ingestion-jobs")
public class AiIngestController {

    @Resource
    private IAiIngestService aiIngestService;

    @Resource
    private MemeApproveService memeApproveService;

    /**
     * 回填已发布梗到 pgvector。
     * POST /admin/v1/ai/ingestion-jobs?limit=500
     */
    @PostMapping
    public Result<AiIngestBackfillVO> backfill(@RequestParam(required = false) Integer limit,
                                               HttpServletRequest request) {
        requireOpsAdmin(request);
        return Result.success(aiIngestService.backfillPublished(limit));
    }

    /**
     * 同步单条梗（status=1 灌库，否则删向量）。
     * POST /admin/v1/ai/ingestion-jobs/memes/{memeId}
     */
    @PostMapping("/memes/{memeId}")
    public Result<Map<String, Object>> syncOne(@PathVariable String memeId,
                                               HttpServletRequest request) {
        requireOpsAdmin(request);
        Long id = RequestIdParser.parseLongId(memeId, "memeId");
        boolean queued = aiIngestService.syncMeme(id);
        return Result.success(Map.of(
                "memeId", String.valueOf(id),
                "queued", queued
        ));
    }

    /**
     * 审核通过并灌库（status → 1）。
     * POST /admin/v1/ai/ingestion-jobs/memes/{memeId}/approvals
     */
    @PostMapping("/memes/{memeId}/approvals")
    public Result<Map<String, Object>> approve(@PathVariable String memeId,
                                               HttpServletRequest request) {
        requireOpsAdmin(request);
        Long id = RequestIdParser.parseLongId(memeId, "memeId");
        memeApproveService.approveToPublished(id);
        return Result.success(Map.of(
                "memeId", String.valueOf(id),
                "status", 1,
                "statusDesc", "正常"
        ));
    }

    private void requireOpsAdmin(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
    }
}
