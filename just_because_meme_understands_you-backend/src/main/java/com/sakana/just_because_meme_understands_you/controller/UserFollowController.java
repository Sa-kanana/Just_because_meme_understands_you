package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.service.user.IUserFollowService;
import com.sakana.just_because_meme_understands_you.vo.FollowBatchStatusVO;
import com.sakana.just_because_meme_understands_you.vo.FollowListPageVO;
import com.sakana.just_because_meme_understands_you.vo.FollowedVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 关注系统：关注 / 取消 / 状态查询 / 关注列表。
 * <p>
 * 路径中 userId 使用 {@code \\d+}，避免与 {@code /user/follow/status} 产生歧义。
 */
@RestController
public class UserFollowController {

    @Resource
    private IUserFollowService userFollowService;

    /**
     * 关注用户
     * POST /user/{userId}/follow
     */
    @PostMapping("/user/{userId:\\d+}/follow")
    public Result<FollowedVO> follow(@PathVariable("userId") String userId,
                                     HttpServletRequest request) {
        Long currentUserId = AuthContext.requireCurrentUserId(request);
        Long targetUserId = AuthContext.parseLongId(userId, "userId");
        return Result.success(userFollowService.follow(currentUserId, targetUserId));
    }

    /**
     * 取消关注
     * DELETE /user/{userId}/follow
     */
    @DeleteMapping("/user/{userId:\\d+}/follow")
    public Result<FollowedVO> unfollow(@PathVariable("userId") String userId,
                                       HttpServletRequest request) {
        Long currentUserId = AuthContext.requireCurrentUserId(request);
        Long targetUserId = AuthContext.parseLongId(userId, "userId");
        return Result.success(userFollowService.unfollow(currentUserId, targetUserId));
    }

    /**
     * 查询关注状态（单人）
     * GET /user/{userId}/follow/status
     */
    @GetMapping("/user/{userId:\\d+}/follow/status")
    public Result<FollowedVO> followStatus(@PathVariable("userId") String userId,
                                           HttpServletRequest request) {
        Long currentUserId = AuthContext.requireCurrentUserId(request);
        Long targetUserId = AuthContext.parseLongId(userId, "userId");
        return Result.success(userFollowService.getFollowStatus(currentUserId, targetUserId));
    }

    /**
     * 批量关注状态（首页作者卡）
     * GET /user/follow/status?userIds=1,2,3
     */
    @GetMapping("/user/follow/status")
    public Result<FollowBatchStatusVO> batchFollowStatus(
            @RequestParam(value = "userIds", required = false) String userIds,
            HttpServletRequest request) {
        Long currentUserId = AuthContext.requireCurrentUserId(request);
        return Result.success(userFollowService.batchFollowStatus(currentUserId, userIds));
    }

    /**
     * 关注列表（可匿名查看）
     * GET /user/{userId}/following?page=&size=
     */
    @GetMapping("/user/{userId:\\d+}/following")
    public Result<FollowListPageVO> pageFollowing(
            @PathVariable("userId") String userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            HttpServletRequest request) {
        Long targetUserId = AuthContext.parseLongId(userId, "userId");
        Long currentUserId = AuthContext.currentUserId(request);
        return Result.success(userFollowService.pageFollowing(targetUserId, currentUserId, page, size));
    }

    /**
     * 粉丝列表（可匿名查看）
     * GET /user/{userId}/followers?page=&size=
     */
    @GetMapping("/user/{userId:\\d+}/followers")
    public Result<FollowListPageVO> pageFollowers(
            @PathVariable("userId") String userId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            HttpServletRequest request) {
        Long targetUserId = AuthContext.parseLongId(userId, "userId");
        Long currentUserId = AuthContext.currentUserId(request);
        return Result.success(userFollowService.pageFollowers(targetUserId, currentUserId, page, size));
    }
}
