package com.sakana.just_because_meme_understands_you.controller.admin;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.admin.AdminUserRoleRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.admin.AdminUserStatusRequestDTO;
import com.sakana.just_because_meme_understands_you.service.admin.IAdminUserService;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminUserListItemVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/users")
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
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return Result.success(adminUserService.page(page, size, keyword, status, role));
    }

    @PatchMapping("/{id}/status")
    public Result<AdminUserListItemVO> updateStatus(
            @PathVariable("id") String id,
            @Valid @RequestBody AdminUserStatusRequestDTO body,
            HttpServletRequest request) {
        Long operatorId = AdminAuthSupport.requireAdmin(request);
        Long targetId = AuthContext.parseLongId(id, "userId");
        return Result.success(adminUserService.updateStatus(operatorId, targetId, body.getStatus()));
    }

    @PatchMapping("/{id}/role")
    public Result<AdminUserListItemVO> updateRole(
            @PathVariable("id") String id,
            @Valid @RequestBody AdminUserRoleRequestDTO body,
            HttpServletRequest request) {
        Long operatorId = AdminAuthSupport.requireAdmin(request);
        Long targetId = AuthContext.parseLongId(id, "userId");
        return Result.success(adminUserService.updateRole(operatorId, targetId, body.getRole()));
    }
}
