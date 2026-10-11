package com.sakana.just_because_meme_understands_you.api.user.controller;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssPolicyRateLimiter;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUploadPolicyService;
import com.sakana.just_because_meme_understands_you.api.user.vo.OssUploadPolicyVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OSS 直传凭证接口
 *
 * @author sakana
 */
@RestController
@RequestMapping("/v1/uploads")
public class OssController {

    @Resource
    private OssUploadPolicyService ossUploadPolicyService;

    @Resource
    private OssPolicyRateLimiter ossPolicyRateLimiter;

    /**
     * 获取前端直传 OSS 的安全签名凭证。
     * 直传落到 tmp/{avatar|memes|comments}/{userId}/…，业务提交时再 promote 到正式目录。
     *
     * GET /oss/policy?fileType=avatar
     */
    @GetMapping("/policies")
    public Result<OssUploadPolicyVO> getUploadPolicy(
            HttpServletRequest request,
            @RequestParam(value = "fileType", required = false) String fileType) {
        Long userId = AuthContext.requireCurrentUserId(request);
        ossPolicyRateLimiter.check(userId);
        return Result.success(ossUploadPolicyService.generatePolicy(fileType));
    }
}
