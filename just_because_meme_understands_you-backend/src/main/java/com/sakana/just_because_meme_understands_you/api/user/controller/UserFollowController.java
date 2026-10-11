package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.modules.user.application.follow.IUserFollowService;
import com.sakana.just_because_meme_understands_you.api.user.vo.FollowBatchStatusVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.FollowListPageVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.FollowedVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

/**
 * 关注系统：关注 / 取消 / 状态查询 / 关注列表。
 */
@RestController
@RequestMapping("/v1/users")
public class UserFollowController {

    @Resource
    private IUserFollowService userFollowService;

    /**
     * 关注用户
     * PUT /v1/users/{userId}/followers/me
     */
    @PutMapping("/{userId}/followers/me")
    public Result<FollowedVO> follow(@PathVariable("userId") String userId,
                                     HttpServletRequest request) {
        Long currentUserId = AuthContext.requireCurrentUserId(request);
        Long targetUserId = RequestIdParser.parseLongId(userId, "userId");
        return Result.success(userFollowService.follow(currentUserId, targetUserId));
    }

    /**
     * 取消关注
     * DELETE /v1/users/{userId}/followers/me
     */
    @DeleteMapping("/{userId}/followers/me")
    public Result<FollowedVO> unfollow(@PathVariable("userId") String userId,
                                       HttpServletRequest request) {
        Long currentUserId = AuthContext.requireCurrentUserId(request);
        Long targetUserId = RequestIdParser.parseLongId(userId, "userId");
        return Result.success(userFollowService.unfollow(currentUserId, targetUserId));
    }

    /**
     * 查询关注状态（单人）
     * GET /v1/users/{userId}/followers/me
     */
    @GetMapping("/{userId}/followers/me")
    public Result<FollowedVO> followStatus(@PathVariable("userId") String userId,
                                           HttpServletRequest request) {
        Long currentUserId = AuthContext.requireCurrentUserId(request);
        Long targetUserId = RequestIdParser.parseLongId(userId, "userId");
        return Result.success(userFollowService.getFollowStatus(currentUserId, targetUserId));
    }

    /**
     * 批量关注状态（首页作者卡）
     * GET /v1/users/me/following?userIds=1,2,3
     */
    @GetMapping("/me/following")
    public Result<FollowBatchStatusVO> batchFollowStatus(
            @RequestParam(value = "userIds", required = false) String userIds,
            HttpServletRequest request) {
        Long currentUserId = AuthContext.requireCurrentUserId(request);
        return Result.success(userFollowService.batchFollowStatus(currentUserId, userIds));
    }

    /**
     * 关注列表（可匿名查看）
     * GET /v1/users/{userId}/following?page=&size=
     */
    @GetMapping("/{userId}/following")
    public Result<FollowListPageVO> pageFollowing(
            @PathVariable("userId") String userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            HttpServletRequest request) {
        Long targetUserId = RequestIdParser.parseLongId(userId, "userId");
        Long currentUserId = AuthContext.currentUserId(request);
        return Result.success(userFollowService.pageFollowing(targetUserId, currentUserId, page, size));
    }

    /**
     * 粉丝列表（可匿名查看）
     * GET /v1/users/{userId}/followers?page=&size=
     */
    @GetMapping("/{userId}/followers")
    public Result<FollowListPageVO> pageFollowers(
            @PathVariable("userId") String userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            HttpServletRequest request) {
        Long targetUserId = RequestIdParser.parseLongId(userId, "userId");
        Long currentUserId = AuthContext.currentUserId(request);
        return Result.success(userFollowService.pageFollowers(targetUserId, currentUserId, page, size));
    }
}
