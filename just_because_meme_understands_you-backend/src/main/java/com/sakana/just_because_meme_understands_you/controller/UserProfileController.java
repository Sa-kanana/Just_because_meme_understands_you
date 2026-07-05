package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.UserProfileUpdateRequestDTO;
import com.sakana.just_because_meme_understands_you.service.user.IUserProfileService;
import com.sakana.just_because_meme_understands_you.vo.EditProfileEchoVO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.UploadAvatarVO;
import com.sakana.just_because_meme_understands_you.vo.UserFavoriteItemVO;
import com.sakana.just_because_meme_understands_you.vo.UserMemePageVO;
import com.sakana.just_because_meme_understands_you.vo.UserMemeItemVO;
import com.sakana.just_because_meme_understands_you.vo.UserProfileVO;
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

@RestController
@RequestMapping("/user")
public class UserProfileController {

    @Resource
    private IUserProfileService userProfileService;

    @GetMapping("/{userId}/profile")
    public Result<UserProfileVO> getProfile(@PathVariable("userId") String userId, HttpServletRequest request) {
        Long targetUserId = AuthContext.parseUserId(userId);
        Long currentUserId = AuthContext.currentUserId(request);
        return Result.success(userProfileService.getUserProfile(targetUserId, currentUserId));
    }

    @GetMapping("/{userId}/memes")
    public Result<UserMemePageVO> pageUserMemes(@PathVariable("userId") String userId,
                                                 HttpServletRequest request,
                                                 @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                 @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        Long targetUserId = AuthContext.parseUserId(userId);
        Long currentUserId = AuthContext.currentUserId(request);
        return Result.success(userProfileService.pageUserMemes(targetUserId, currentUserId, page, size));
    }

    @GetMapping("/{userId}/favorites")
    public Result<PageVO<UserFavoriteItemVO>> pageUserFavorites(@PathVariable("userId") String userId,
                                                                 @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                                 @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        Long targetUserId = AuthContext.parseUserId(userId);
        return Result.success(userProfileService.pageUserFavorites(targetUserId, page, size));
    }

    @GetMapping("/me/profile")
    public Result<EditProfileEchoVO> getMyProfileEcho(HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(userProfileService.getEditProfileEcho(userId));
    }

    @PostMapping("/avatar/upload")
    public Result<UploadAvatarVO> uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Long userId = AuthContext.currentUserId(request);
        return Result.success(userProfileService.uploadAvatar(userId, file));
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UserProfileUpdateRequestDTO request, HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.currentUserId(httpServletRequest);
        userProfileService.updateProfile(userId, request);
        return Result.success();
    }
}
