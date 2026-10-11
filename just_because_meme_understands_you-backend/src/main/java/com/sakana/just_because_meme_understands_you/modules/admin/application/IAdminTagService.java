package com.sakana.just_because_meme_understands_you.modules.admin.application;

import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminTagSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminTagVO;

public interface IAdminTagService {

    PageVO<AdminTagVO> page(Integer page, Integer size, String keyword);

    AdminTagVO create(AdminTagSaveRequestDTO request);

    AdminTagVO update(Integer id, AdminTagSaveRequestDTO request);

    void delete(Integer id);
}
