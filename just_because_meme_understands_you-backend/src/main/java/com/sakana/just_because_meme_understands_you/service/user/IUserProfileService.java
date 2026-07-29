package com.sakana.just_because_meme_understands_you.service.user;

import com.sakana.just_because_meme_understands_you.dto.UserProfileUpdateRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.AccountProfileVO;
import com.sakana.just_because_meme_understands_you.vo.EditProfileEchoVO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.UploadAvatarVO;
import com.sakana.just_because_meme_understands_you.vo.UserFavoriteItemVO;
import com.sakana.just_because_meme_understands_you.vo.UserMemeItemVO;
import com.sakana.just_because_meme_understands_you.vo.UserMemePageVO;
import com.sakana.just_because_meme_understands_you.vo.UserProfileVO;
import org.springframework.web.multipart.MultipartFile;

public interface IUserProfileService {

    UserProfileVO getUserProfile(Long targetUserId, Long currentUserId);

    EditProfileEchoVO getEditProfileEcho(Long userId);

    /**
     * 分页获取用户发布的梗。
     * 状态隔离：currentUserId 与 targetUserId 一致返回所有状态（可按 status 筛选），否则只返回 status=1。
     * @param status 可选；仅本人有效：1已发布 / 2审核中(含6) / 3主动下架 / 5锁定；null 表示全部
     */
    UserMemePageVO pageUserMemes(Long targetUserId, Long currentUserId, Integer page, Integer size, Integer status);

    /**
     * 分页获取用户收藏的梗。
     * 权限：本人可查任意 folderId（含默认夹 0）；他人仅可查 targetUser 的公开自定义夹，folderId=0 拒绝。
     */
    PageVO<UserFavoriteItemVO> pageUserFavorites(Long targetUserId, Long currentUserId, Long folderId, Integer page, Integer size);

    UploadAvatarVO uploadAvatar(Long userId, MultipartFile file);

    /**
     * 更新个人资料，返回最新资料快照（对齐 Apifox Author）。
     */
    AccountProfileVO updateProfile(Long userId, UserProfileUpdateRequestDTO request);

    void evictUserCache(Long userId);

    /**
     * 清理指定用户发布列表的 Redis ZSet 缓存，发布/下架后调用。
     */
    void evictUserMemesCache(Long userId);
}
