package com.sakana.just_because_meme_understands_you.modules.admin.application;

import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminSensitiveWordSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminSensitiveWordVO;

public interface IAdminSensitiveWordService {

    PageVO<AdminSensitiveWordVO> page(Integer page, Integer size, String keyword, String category, String level,
                                      Integer status);

    AdminSensitiveWordVO create(Long adminUserId, AdminSensitiveWordSaveRequestDTO request);

    AdminSensitiveWordVO update(Long id, AdminSensitiveWordSaveRequestDTO request);

    void delete(Long id);

    AdminSensitiveWordVO updateStatus(Long id, Integer status);
}
