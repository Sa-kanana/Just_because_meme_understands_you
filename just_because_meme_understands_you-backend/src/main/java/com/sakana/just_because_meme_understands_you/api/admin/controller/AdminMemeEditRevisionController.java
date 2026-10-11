package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminMemeEditReviewRequestDTO;
import com.sakana.just_because_meme_understands_you.api.admin.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeEditRevisionVO;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;
import com.sakana.just_because_meme_understands_you.modules.meme.application.MemeEditRevisionReviewService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 中文注释：管理端作者梗修改稿审核接口。 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequestMapping("/admin/v1/meme-edit-revisions")
public class AdminMemeEditRevisionController {

    @Resource
    private MemeEditRevisionReviewService reviewService;

    /** 中文注释：查询等待审核的梗修改稿。 */
    @GetMapping
    public Result<PageVO<AdminMemeEditRevisionVO>> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return Result.success(reviewService.pagePending(page, size));
    }

    /** 中文注释：查看正式内容和作者修改内容的对照详情。 */
    @GetMapping("/{id}")
    public Result<AdminMemeEditRevisionVO> detail(@PathVariable("id") String id,
                                                  HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return Result.success(reviewService.detail(RequestIdParser.parseLongId(id, "revisionId")));
    }

    /** 中文注释：通过或驳回修改稿；驳回说明由服务层强制校验非空。 */
    @PatchMapping("/{id}")
    public Result<AdminMemeEditRevisionVO> review(@PathVariable("id") String id,
                                                  @Valid @RequestBody AdminMemeEditReviewRequestDTO body,
                                                  HttpServletRequest request) {
        Long adminUserId = AdminAuthSupport.requireAdmin(request);
        Long revisionId = RequestIdParser.parseLongId(id, "revisionId");
        AdminMemeEditRevisionVO result = "APPROVED".equals(body.getStatus())
                ? reviewService.approve(revisionId, adminUserId)
                : reviewService.reject(revisionId, adminUserId, body.getNote());
        return Result.success(result);
    }
}
