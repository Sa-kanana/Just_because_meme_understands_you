package com.sakana.just_because_meme_understands_you.api.user.controller;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.api.user.dto.UserProfileUpdateRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
import com.sakana.just_because_meme_understands_you.api.user.vo.AccountProfileVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.EditProfileEchoVO;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.UploadAvatarVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.UserFavoriteItemVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.UserMemePageVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.UserMemeItemVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.UserProfileVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

@RestController
@RequestMapping("/v1/users")
public class UserProfileController {

    @Resource
    private IUserProfileService userProfileService;

    @GetMapping("/{userId}")
    public Result<UserProfileVO> getProfile(@PathVariable("userId") String userId, HttpServletRequest request) {
        Long targetUserId = RequestIdParser.parseLong(userId, "userId");
        Long currentUserId = AuthContext.currentUserId(request);
        return Result.success(userProfileService.getUserProfile(targetUserId, currentUserId));
    }

    @GetMapping("/{userId}/memes")
    public Result<UserMemePageVO> pageUserMemes(@PathVariable("userId") String userId,
                                                 HttpServletRequest request,
                                                 @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                 @RequestParam(value = "size", required = false, defaultValue = "10") Integer size,
                                                 @RequestParam(value = "status", required = false) Integer status) {
        Long targetUserId = RequestIdParser.parseLong(userId, "userId");
        Long currentUserId = AuthContext.currentUserId(request);
        return Result.success(userProfileService.pageUserMemes(targetUserId, currentUserId, page, size, status));
    }

    @GetMapping("/{userId}/favorites")
    public Result<PageVO<UserFavoriteItemVO>> pageUserFavorites(@PathVariable("userId") String userId,
                                                                 HttpServletRequest request,
                                                                 @RequestParam(value = "folderId", required = false) Long folderId,
                                                                 @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                                 @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        Long targetUserId = RequestIdParser.parseLong(userId, "userId");
        Long currentUserId = AuthContext.currentUserId(request);
        return Result.success(userProfileService.pageUserFavorites(targetUserId, currentUserId, folderId, page, size));
    }

    @GetMapping("/me")
    public Result<EditProfileEchoVO> getMyProfileEcho(HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(userProfileService.getEditProfileEcho(userId));
    }

    @PostMapping("/me/avatar")
    public Result<UploadAvatarVO> uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(userProfileService.uploadAvatar(userId, file));
    }

    @PutMapping("/me")
    public Result<AccountProfileVO> updateProfile(@RequestBody UserProfileUpdateRequestDTO request,
                                                  HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(userProfileService.updateProfile(userId, request));
    }
}
