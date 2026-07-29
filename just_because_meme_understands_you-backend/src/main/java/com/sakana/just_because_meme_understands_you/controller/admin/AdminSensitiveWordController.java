package com.sakana.just_because_meme_understands_you.controller.admin;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.admin.AdminSensitiveWordSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.admin.AdminSensitiveWordStatusRequestDTO;
import com.sakana.just_because_meme_understands_you.service.admin.IAdminSensitiveWordService;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminSensitiveWordVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/sensitive-words")
public class AdminSensitiveWordController {

    @Resource
    private IAdminSensitiveWordService adminSensitiveWordService;

    @GetMapping
    public Result<PageVO<AdminSensitiveWordVO>> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "status", required = false) Integer status,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return Result.success(adminSensitiveWordService.page(page, size, keyword, category, status));
    }

    @PostMapping
    public Result<AdminSensitiveWordVO> create(
            @Valid @RequestBody AdminSensitiveWordSaveRequestDTO body,
            HttpServletRequest request) {
        Long adminId = AdminAuthSupport.requireAdmin(request);
        return Result.success(adminSensitiveWordService.create(adminId, body));
    }

    @PutMapping("/{id}")
    public Result<AdminSensitiveWordVO> update(
            @PathVariable("id") String id,
            @Valid @RequestBody AdminSensitiveWordSaveRequestDTO body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long wordId = AuthContext.parseLongId(id, "id");
        return Result.success(adminSensitiveWordService.update(wordId, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long wordId = AuthContext.parseLongId(id, "id");
        adminSensitiveWordService.delete(wordId);
        return Result.success();
    }

    @PatchMapping("/{id}/status")
    public Result<AdminSensitiveWordVO> updateStatus(
            @PathVariable("id") String id,
            @Valid @RequestBody AdminSensitiveWordStatusRequestDTO body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long wordId = AuthContext.parseLongId(id, "id");
        return Result.success(adminSensitiveWordService.updateStatus(wordId, body.getStatus()));
    }
}
