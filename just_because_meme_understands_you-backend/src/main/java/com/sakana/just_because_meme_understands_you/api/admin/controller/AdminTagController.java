package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.api.admin.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminTagSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminTagService;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminTagVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequestMapping("/admin/v1/tags")
public class AdminTagController {

    @Resource
    private IAdminTagService adminTagService;

    @GetMapping
    public Result<PageVO<AdminTagVO>> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "keyword", required = false) String keyword,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return Result.success(adminTagService.page(page, size, keyword));
    }

    @PostMapping
    public Result<AdminTagVO> create(
            @Valid @RequestBody AdminTagSaveRequestDTO body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return Result.success(adminTagService.create(body));
    }

    @PutMapping("/{id}")
    public Result<AdminTagVO> update(
            @PathVariable("id") String id,
            @Valid @RequestBody AdminTagSaveRequestDTO body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return Result.success(adminTagService.update(parseTagId(id), body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        adminTagService.delete(parseTagId(id));
        return Result.success();
    }

    private static Integer parseTagId(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "id 不能为空");
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            if (parsed <= 0) {
                throw new BizException(Result.CODE_BAD_REQUEST, "id 不合法");
            }
            return parsed;
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, "id 格式错误");
        }
    }
}
