package com.sakana.just_because_meme_understands_you.modules.admin.application;

import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminHomeImageSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminHomeImageVO;

public interface IAdminHomeImageService {

    PageVO<AdminHomeImageVO> page(Integer page, Integer size, Integer status);

    AdminHomeImageVO create(Long adminUserId, AdminHomeImageSaveRequestDTO request);

    AdminHomeImageVO update(Long adminUserId, Long id, AdminHomeImageSaveRequestDTO request);

    void delete(Long id);

    AdminHomeImageVO updateStatus(Long id, Integer status);
}
