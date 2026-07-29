package com.sakana.just_because_meme_understands_you.service.admin;

import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminMemeActionVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminMemeDetailVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminMemeListItemVO;

public interface IAdminMemeService {

    PageVO<AdminMemeListItemVO> page(Integer page, Integer size, Integer status, String keyword, Long userId);

    AdminMemeDetailVO detail(Long memeId);

    AdminMemeActionVO approve(Long memeId);

    AdminMemeActionVO reject(Long memeId);

    AdminMemeActionVO offline(Long memeId);

    AdminMemeActionVO online(Long memeId);
}
