package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.meme.application.MemeCrawlService;
import com.sakana.just_because_meme_understands_you.api.admin.vo.MemeCrawlResultVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import com.sakana.just_because_meme_understands_you.api.admin.support.AdminAuthSupport;

/**
 * 热梗采集运维入口（管理员）。
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequestMapping("/admin/v1/ai/crawl-jobs")
public class AiCrawlController {

    @Resource
    private MemeCrawlService memeCrawlService;

    /**
     * 手动触发一次 Firecrawl 热梗采集并入库。
     * POST /admin/v1/ai/crawl-jobs?limit=5
     */
    @PostMapping
    public Result<MemeCrawlResultVO> trigger(
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request) {
        requireOpsAdmin(request);
        return Result.success(memeCrawlService.crawlAndPersist(limit));
    }

    private void requireOpsAdmin(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
    }
}
