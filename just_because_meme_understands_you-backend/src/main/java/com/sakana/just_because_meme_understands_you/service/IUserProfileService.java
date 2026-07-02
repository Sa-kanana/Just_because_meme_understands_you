package com.sakana.just_because_meme_understands_you.service;

import com.sakana.just_because_meme_understands_you.dto.UserProfileUpdateRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.EditProfileEchoVO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.UploadAvatarVO;
import com.sakana.just_because_meme_understands_you.vo.UserFavoriteItemVO;
import com.sakana.just_because_meme_understands_you.vo.UserMemeItemVO;
import com.sakana.just_because_meme_understands_you.vo.UserProfileVO;
import org.springframework.web.multipart.MultipartFile;

public interface IUserProfileService {

    UserProfileVO getUserProfile(Long targetUserId, Long currentUserId);

    EditProfileEchoVO getEditProfileEcho(Long userId);

    PageVO<UserMemeItemVO> pageUserMemes(Long userId, Integer page, Integer size);

    PageVO<UserFavoriteItemVO> pageUserFavorites(Long userId, Integer page, Integer size);

    UploadAvatarVO uploadAvatar(Long userId, MultipartFile file);

    void updateProfile(Long userId, UserProfileUpdateRequestDTO request);
}
