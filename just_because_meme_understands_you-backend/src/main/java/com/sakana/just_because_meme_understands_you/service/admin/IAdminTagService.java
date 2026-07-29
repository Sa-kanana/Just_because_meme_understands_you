package com.sakana.just_because_meme_understands_you.service.admin;

import com.sakana.just_because_meme_understands_you.dto.admin.AdminTagSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminTagVO;

public interface IAdminTagService {

    PageVO<AdminTagVO> page(Integer page, Integer size, String keyword);

    AdminTagVO create(AdminTagSaveRequestDTO request);

    AdminTagVO update(Integer id, AdminTagSaveRequestDTO request);

    void delete(Integer id);
}
