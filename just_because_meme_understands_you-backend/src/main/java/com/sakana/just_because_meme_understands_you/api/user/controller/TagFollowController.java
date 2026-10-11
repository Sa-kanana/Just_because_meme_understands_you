package com.sakana.just_because_meme_understands_you.api.user.controller;

import org.springframework.web.bind.annotation.RequestMapping;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.modules.user.application.follow.TagFollowService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/users/me/followed-tags")
public class TagFollowController {
    @Resource private TagFollowService service;

    @GetMapping
    public Result<List<MemeTagVO>> list(HttpServletRequest request) {
        return Result.success(service.list(AuthContext.requireCurrentUserId(request)));
    }

    @PutMapping("/{tagId}")
    public Result<Void> follow(@PathVariable Integer tagId, HttpServletRequest request) {
        service.follow(AuthContext.requireCurrentUserId(request), tagId);
        return Result.success();
    }

    @DeleteMapping("/{tagId}")
    public Result<Void> unfollow(@PathVariable Integer tagId, HttpServletRequest request) {
        service.unfollow(AuthContext.requireCurrentUserId(request), tagId);
        return Result.success();
    }
}
