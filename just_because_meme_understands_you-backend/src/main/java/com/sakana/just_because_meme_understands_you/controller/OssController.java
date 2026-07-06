package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.service.oss.OssPolicyRateLimiter;
import com.sakana.just_because_meme_understands_you.service.oss.OssUploadPolicyService;
import com.sakana.just_because_meme_understands_you.vo.OssUploadPolicyVO;
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
@RequestMapping("/oss")
public class OssController {

    @Resource
    private OssUploadPolicyService ossUploadPolicyService;

    @Resource
    private OssPolicyRateLimiter ossPolicyRateLimiter;

    /**
     * 获取前端直传 OSS 的安全签名凭证。
     * 前端根据 fileType 将图片分目录存放：avatar / meme / comment / home。
     *
     * GET /oss/policy?fileType=avatar
     */
    @GetMapping("/policy")
    public Result<OssUploadPolicyVO> getUploadPolicy(
            HttpServletRequest request,
            @RequestParam(value = "fileType", required = false) String fileType) {
        ossPolicyRateLimiter.check(request);
        return Result.success(ossUploadPolicyService.generatePolicy(fileType));
    }
}
