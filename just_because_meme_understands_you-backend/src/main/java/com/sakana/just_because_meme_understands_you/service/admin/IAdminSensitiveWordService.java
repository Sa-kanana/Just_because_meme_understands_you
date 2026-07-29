package com.sakana.just_because_meme_understands_you.service.admin;

import com.sakana.just_because_meme_understands_you.dto.admin.AdminSensitiveWordSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminSensitiveWordVO;

public interface IAdminSensitiveWordService {

    PageVO<AdminSensitiveWordVO> page(Integer page, Integer size, String keyword, String category, Integer status);

    AdminSensitiveWordVO create(Long adminUserId, AdminSensitiveWordSaveRequestDTO request);

    AdminSensitiveWordVO update(Long id, AdminSensitiveWordSaveRequestDTO request);

    void delete(Long id);

    AdminSensitiveWordVO updateStatus(Long id, Integer status);
}
