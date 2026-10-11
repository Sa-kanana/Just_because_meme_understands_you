package com.sakana.just_because_meme_understands_you.api.user.controller;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.api.user.dto.FavoriteFolderCreateDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.FavoriteFolderReorderDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.FavoriteFolderUpdateDTO;
import com.sakana.just_because_meme_understands_you.modules.user.application.favorite.IUserFavoriteFolderService;
import com.sakana.just_because_meme_understands_you.api.user.vo.FavoriteFolderListVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.FavoriteFolderVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sakana.just_because_meme_understands_you.api.user.support.FavoriteFolderIdParser;
import com.sakana.just_because_meme_understands_you.common.validation.RequestIdParser;

/**
 * 收藏夹管理（本人）：/v1/users/me/favorite-folders
 * 他人查看公开夹：/v1/users/{userId}/favorite-folders
 */
@RestController
@RequestMapping("/v1/users")
public class FavoriteFolderController {

    @Resource
    private IUserFavoriteFolderService favoriteFolderService;

    /** 本人收藏夹列表（含默认夹） */
    @GetMapping("/me/favorite-folders")
    public Result<FavoriteFolderListVO> myFolders(HttpServletRequest request) {
        Long userId = AuthContext.requireCurrentUserId(request);
        return Result.success(favoriteFolderService.listMyFolders(userId));
    }

    /** 他人收藏夹列表（仅公开自定义夹）；本人同 /me */
    @GetMapping("/{userId}/favorite-folders")
    public Result<FavoriteFolderListVO> folders(@PathVariable("userId") String userId,
                                                  HttpServletRequest request) {
        Long targetUserId = RequestIdParser.parseLong(userId, "userId");
        Long currentUserId = AuthContext.currentUserId(request);
        return Result.success(favoriteFolderService.listFolders(targetUserId, currentUserId));
    }

    @PostMapping("/me/favorite-folders")
    public Result<FavoriteFolderVO> create(@RequestBody FavoriteFolderCreateDTO request,
                                           HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(favoriteFolderService.createFolder(userId, request));
    }

    @PutMapping("/me/favorite-folders/{folderId}")
    public Result<Void> update(@PathVariable("folderId") String folderId,
                               @RequestBody FavoriteFolderUpdateDTO request,
                               HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        favoriteFolderService.updateFolder(userId, FavoriteFolderIdParser.parseFolderId(folderId), request);
        return Result.success();
    }

    @DeleteMapping("/me/favorite-folders/{folderId}")
    public Result<Void> delete(@PathVariable("folderId") String folderId,
                               HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        favoriteFolderService.deleteFolder(userId, FavoriteFolderIdParser.parseFolderId(folderId));
        return Result.success();
    }

    @PutMapping("/me/favorite-folders/order")
    public Result<Void> reorder(@RequestBody FavoriteFolderReorderDTO request,
                                HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        favoriteFolderService.reorderFolders(userId, request);
        return Result.success();
    }
}
