package com.sakana.just_because_meme_understands_you.service.admin;

import com.sakana.just_because_meme_understands_you.dto.admin.AdminHomeImageSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminHomeImageVO;

public interface IAdminHomeImageService {

    PageVO<AdminHomeImageVO> page(Integer page, Integer size, Integer status);

    AdminHomeImageVO create(Long adminUserId, AdminHomeImageSaveRequestDTO request);

    AdminHomeImageVO update(Long adminUserId, Long id, AdminHomeImageSaveRequestDTO request);

    void delete(Long id);

    AdminHomeImageVO updateStatus(Long id, Integer status);
}
