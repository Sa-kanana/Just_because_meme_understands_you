package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminReportResolutionDTO;
import com.sakana.just_because_meme_understands_you.api.admin.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminReportVO;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;
import com.sakana.just_because_meme_understands_you.modules.report.application.MemeReportService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

/** 管理员举报队列与处理。 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequestMapping("/admin/v1/reports")
public class AdminReportController {
    @Resource private MemeReportService service;

    @GetMapping
    public Result<PageVO<AdminReportVO>> page(@RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer size,
                                                @RequestParam(required = false) String status,
                                                HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return Result.success(service.page(page, size, status));
    }

    @PatchMapping("/{id}")
    public Result<AdminReportVO> resolve(@PathVariable String id,
                                         @Valid @RequestBody AdminReportResolutionDTO body,
                                         HttpServletRequest request) {
        Long adminId = AdminAuthSupport.requireAdmin(request);
        return Result.success(service.resolve(RequestIdParser.parseLongId(id, "id"), adminId,
                body.getResult(), body.getNote()));
    }
}
