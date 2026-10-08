package com.sakana.just_because_meme_understands_you.api.user.controller;

import com.sakana.just_because_meme_understands_you.api.user.vo.UserSuppressionItemVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.UserSuppressionStatusVO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;
import com.sakana.just_because_meme_understands_you.modules.user.application.safety.UserSafetyService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 当前用户的静音、屏蔽关系管理接口。 */
@RestController
@RequestMapping("/v1/users/me/suppressions")
public class UserSuppressionController {
    @Resource private UserSafetyService userSafetyService;

    @GetMapping
    public Result<List<UserSuppressionItemVO>> list(HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(userSafetyService.list(userId));
    }

    @GetMapping("/{targetId}")
    public Result<UserSuppressionStatusVO> status(@PathVariable String targetId, HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(userSafetyService.status(userId,
                RequestIdParser.parseLongId(targetId, "targetId")));
    }

    @PutMapping("/{targetId}")
    public Result<UserSuppressionStatusVO> apply(@PathVariable String targetId,
                                                  @RequestParam String type,
                                                  HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(userSafetyService.apply(userId,
                RequestIdParser.parseLongId(targetId, "targetId"), type));
    }

    @DeleteMapping("/{targetId}")
    public Result<UserSuppressionStatusVO> remove(@PathVariable String targetId,
                                                   @RequestParam String type,
                                                   HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(userSafetyService.remove(userId,
                RequestIdParser.parseLongId(targetId, "targetId"), type));
    }
}
