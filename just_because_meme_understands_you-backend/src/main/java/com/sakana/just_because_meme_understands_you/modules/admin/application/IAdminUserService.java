package com.sakana.just_because_meme_understands_you.modules.admin.application;

import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminUserListItemVO;

public interface IAdminUserService {

    PageVO<AdminUserListItemVO> page(Integer page, Integer size, String keyword, Integer status, String role);

    AdminUserListItemVO updateStatus(Long operatorId, Long targetUserId, Integer status);

    AdminUserListItemVO updateRole(Long operatorId, Long targetUserId, String role);
}
