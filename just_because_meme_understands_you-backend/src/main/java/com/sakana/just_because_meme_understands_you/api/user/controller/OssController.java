package com.sakana.just_because_meme_understands_you.api.user.controller;

import com.sakana.just_because_meme_understands_you.api.user.dto.OssUploadTicketRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.OssUploadPolicyVO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetUploadConfirmationService;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssPolicyRateLimiter;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUploadPolicyService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 中文注释：签发 OSS 图片上传票据并确认客户端直传结果的接口。 */
@RestController
@RequestMapping("/v1/uploads")
public class OssController {

    /** 中文注释：校验上传声明并创建服务端对象键及签名 policy 的应用服务。 */
    @Resource
    private OssUploadPolicyService ossUploadPolicyService;

    /** 中文注释：校验 OSS 对象已上传并登记元数据的应用服务。 */
    @Resource
    private FileAssetUploadConfirmationService fileAssetUploadConfirmationService;

    /** 中文注释：限制当前用户申请上传票据的频率。 */
    @Resource
    private OssPolicyRateLimiter ossPolicyRateLimiter;

    /**
     * 中文注释：为当前登录用户创建服务端分配对象键的临时上传票据。
     *
     * @param request 当前认证 HTTP 请求
     * @param uploadRequest 上传场景、文件名、MIME 和文件大小声明
     * @return 带资产ID、精确对象键和 OSS 签名信息的上传票据
     */
    @PostMapping("/tickets")
    public Result<OssUploadPolicyVO> createUploadTicket(
            HttpServletRequest request,
            @Valid @RequestBody OssUploadTicketRequestDTO uploadRequest) {
        Long userId = AuthContext.requireCurrentUserId(request);
        ossPolicyRateLimiter.check(userId);
        return Result.success(ossUploadPolicyService.createTicket(uploadRequest, request));
    }

    /**
     * 中文注释：确认指定资产已成功上传到 OSS，并核对对象大小及 MIME 元数据。
     *
     * @param request 当前认证 HTTP 请求
     * @param assetId 创建上传票据时返回的文件资产ID
     * @return 上传确认结果
     */
    @PostMapping("/{assetId}/confirm")
    public Result<Void> confirmUpload(HttpServletRequest request, @PathVariable Long assetId) {
        Long userId = AuthContext.requireCurrentUserId(request);
        ossPolicyRateLimiter.check(userId);
        fileAssetUploadConfirmationService.confirmUpload(userId, assetId);
        return Result.success(null);
    }
}
