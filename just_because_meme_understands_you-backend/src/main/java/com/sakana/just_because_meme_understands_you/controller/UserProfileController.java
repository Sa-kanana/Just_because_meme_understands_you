package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.dto.UserProfileUpdateRequestDTO;
import com.sakana.just_because_meme_understands_you.service.IUserProfileService;
import com.sakana.just_because_meme_understands_you.vo.EditProfileEchoVO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.UploadAvatarVO;
import com.sakana.just_because_meme_understands_you.vo.UserFavoriteItemVO;
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
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/user")
public class UserProfileController {

    @Resource
    private IUserProfileService userProfileService;

    @GetMapping("/{userId}/profile")
    public Result<UserProfileVO> getProfile(@PathVariable("userId") String userId, HttpServletRequest request) {
        Long targetUserId = parseUserId(userId);
        Long currentUserId = currentUserId(request);
        return Result.success(userProfileService.getUserProfile(targetUserId, currentUserId));
    }

    @GetMapping("/{userId}/memes")
    public Result<PageVO<UserMemeItemVO>> pageUserMemes(@PathVariable("userId") String userId,
                                                         @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                         @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        Long targetUserId = parseUserId(userId);
        return Result.success(userProfileService.pageUserMemes(targetUserId, page, size));
    }

    @GetMapping("/{userId}/favorites")
    public Result<PageVO<UserFavoriteItemVO>> pageUserFavorites(@PathVariable("userId") String userId,
                                                                 @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                                                 @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        Long targetUserId = parseUserId(userId);
        return Result.success(userProfileService.pageUserFavorites(targetUserId, page, size));
    }

    @GetMapping("/me/profile")
    public Result<EditProfileEchoVO> getMyProfileEcho(HttpServletRequest request) {
        Long userId = requireCurrentUserId(request);
        return Result.success(userProfileService.getEditProfileEcho(userId));
    }

    @PostMapping("/avatar/upload")
    public Result<UploadAvatarVO> uploadAvatar(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        Long userId = currentUserId(request);
        return Result.success(userProfileService.uploadAvatar(userId, file));
    }

    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody UserProfileUpdateRequestDTO request, HttpServletRequest httpServletRequest) {
        Long userId = currentUserId(httpServletRequest);
        userProfileService.updateProfile(userId, request);
        return Result.success();
    }

    private Long currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            return null;
        }
        return Long.parseLong(String.valueOf(userId));
    }

    private Long requireCurrentUserId(HttpServletRequest request) {
        Long userId = currentUserId(request);
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        return userId;
    }

    private Long parseUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "userId 不能为空");
        }
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_BAD_REQUEST, "userId 格式错误");
        }
    }
}
