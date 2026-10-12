package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.sakana.just_because_meme_understands_you.api.admin.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AiVectorReconciliationJobVO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.ai.application.MemeVectorReconciliationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端手动启动、查询或继续梗向量校准任务。 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequestMapping("/admin/v1/ai/vector-index/reconciliation-jobs")
public class AiVectorReconciliationController {

    private final MemeVectorReconciliationService reconciliationService;

    public AiVectorReconciliationController(MemeVectorReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /** 手动启动校准；已有活动任务时返回已有任务进度。 */
    @PostMapping
    public Result<AiVectorReconciliationJobVO> start(HttpServletRequest request) {
        long adminId = AdminAuthSupport.requireAdmin(request);
        return Result.success(reconciliationService.start(adminId));
    }

    /** 查询单个校准任务进度。 */
    @GetMapping("/{jobId}")
    public Result<AiVectorReconciliationJobVO> find(
            @PathVariable("jobId") long jobId, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return Result.success(reconciliationService.find(jobId));
    }

    /** 管理员继续失败的校准任务。 */
    @PostMapping("/{jobId}/resume")
    public Result<AiVectorReconciliationJobVO> resume(
            @PathVariable("jobId") long jobId, HttpServletRequest request) {
        long adminId = AdminAuthSupport.requireAdmin(request);
        return Result.success(reconciliationService.resume(jobId, adminId));
    }
}
