package com.sakana.just_because_meme_understands_you.api.admin.controller;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.api.admin.support.AdminAuthSupport;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminHomeImageSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminHomeImageStatusRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminHomeImageService;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminHomeImageVO;
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
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

@RestController
@RequestMapping("/v1/admin/home-images")
public class AdminHomeImageController {

    @Resource
    private IAdminHomeImageService adminHomeImageService;

    @GetMapping
    public Result<PageVO<AdminHomeImageVO>> page(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "status", required = false) Integer status,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return Result.success(adminHomeImageService.page(page, size, status));
    }

    @PostMapping
    public Result<AdminHomeImageVO> create(
            @Valid @RequestBody AdminHomeImageSaveRequestDTO body,
            HttpServletRequest request) {
        Long adminId = AdminAuthSupport.requireAdmin(request);
        return Result.success(adminHomeImageService.create(adminId, body));
    }

    @PutMapping("/{id}")
    public Result<AdminHomeImageVO> update(
            @PathVariable("id") String id,
            @Valid @RequestBody AdminHomeImageSaveRequestDTO body,
            HttpServletRequest request) {
        Long adminId = AdminAuthSupport.requireAdmin(request);
        Long imageId = RequestIdParser.parseLongId(id, "id");
        return Result.success(adminHomeImageService.update(adminId, imageId, body));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") String id, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long imageId = RequestIdParser.parseLongId(id, "id");
        adminHomeImageService.delete(imageId);
        return Result.success();
    }

    @PatchMapping("/{id}")
    public Result<AdminHomeImageVO> updateStatus(
            @PathVariable("id") String id,
            @Valid @RequestBody AdminHomeImageStatusRequestDTO body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        Long imageId = RequestIdParser.parseLongId(id, "id");
        return Result.success(adminHomeImageService.updateStatus(imageId, body.getStatus()));
    }
}
