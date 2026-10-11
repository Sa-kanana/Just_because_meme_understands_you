package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.api.admin.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminUserPatchRequestDTO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminUserService;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminUserListItemVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

@RestController
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequestMapping("/admin/v1/users")
public class AdminUserController {

    @Resource
    private IAdminUserService adminUserService;

    @GetMapping
    public Result<PageVO<AdminUserListItemVO>> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "deleted", required = false, defaultValue = "false") boolean deleted,
            HttpServletRequest request) {
        Long operatorId = AdminAuthSupport.requireAdmin(request);
        return Result.success(adminUserService.page(operatorId, page, size, keyword, status, role, deleted));
    }

    @PatchMapping("/{id}")
    public Result<AdminUserListItemVO> patchUser(
            @PathVariable("id") String id,
            @Valid @RequestBody AdminUserPatchRequestDTO body,
            HttpServletRequest request) {
        Long operatorId = AdminAuthSupport.requireAdmin(request);
        Long targetId = RequestIdParser.parseLongId(id, "userId");
        if ((body.getStatus() == null) == (body.getRole() == null)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "一次只能修改用户状态或角色中的一项");
        }
        if (body.getStatus() != null) {
            return Result.success(adminUserService.updateStatus(operatorId, targetId, body.getStatus()));
        }
        return Result.success(adminUserService.updateRole(operatorId, targetId, body.getRole()));
    }

    @DeleteMapping("/{id}")
    public Result<AdminUserListItemVO> deleteUser(
            @PathVariable("id") String id,
            HttpServletRequest request) {
        Long operatorId = AdminAuthSupport.requireAdmin(request);
        Long targetId = RequestIdParser.parseLongId(id, "userId");
        return Result.success(adminUserService.delete(operatorId, targetId));
    }

    @PostMapping("/{id}/restore")
    public Result<AdminUserListItemVO> restoreUser(
            @PathVariable("id") String id,
            HttpServletRequest request) {
        Long operatorId = AdminAuthSupport.requireAdmin(request);
        Long targetId = RequestIdParser.parseLongId(id, "userId");
        return Result.success(adminUserService.restore(operatorId, targetId));
    }
}
