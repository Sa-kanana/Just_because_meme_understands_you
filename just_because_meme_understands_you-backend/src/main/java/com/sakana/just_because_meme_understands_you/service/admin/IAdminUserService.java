package com.sakana.just_because_meme_understands_you.service.admin;

import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminUserListItemVO;

public interface IAdminUserService {

    PageVO<AdminUserListItemVO> page(Integer page, Integer size, String keyword, Integer status, String role);

    AdminUserListItemVO updateStatus(Long operatorId, Long targetUserId, Integer status);

    AdminUserListItemVO updateRole(Long operatorId, Long targetUserId, String role);
}
