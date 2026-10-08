package com.sakana.just_because_meme_understands_you.modules.admin.application;

import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeActionVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeDetailVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeListItemVO;

public interface IAdminMemeService {

    PageVO<AdminMemeListItemVO> page(Integer page, Integer size, Integer status, String keyword, Long userId);

    AdminMemeDetailVO detail(Long memeId);

    AdminMemeActionVO approve(Long memeId);

    AdminMemeActionVO reject(Long memeId);

    AdminMemeActionVO offline(Long memeId);

    AdminMemeActionVO online(Long memeId);
}
