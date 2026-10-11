package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.admin.application.assembler.AdminViewMapper;
import com.sakana.just_because_meme_understands_you.modules.auth.application.UserSessionRevoker;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.modules.user.application.UserRoleService;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceRoleProtectionTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private AdminViewMapper adminViewMapper;

    @Mock
    private OssUrlHelper ossUrlHelper;

    @Mock
    private UserRoleService userRoleService;

    @Mock
    private UserSessionRevoker userSessionRevoker;

    @InjectMocks
    private AdminUserServiceImpl service;

    @Test
    void rejectsDemotingTheLastSuperAdmin() {
        when(userRoleService.hasAnyRole(11L, UserRoleService.SUPER_ADMIN)).thenReturn(true);
        when(userRoleService.getRoleCodes(22L)).thenReturn(List.of(UserRoleService.SUPER_ADMIN, UserRoleService.USER));
        when(userRoleService.lockUserIdsByRole(UserRoleService.SUPER_ADMIN)).thenReturn(List.of(22L));
        when(userMapper.selectById(22L)).thenReturn(user(22L));

        BizException exception = assertThrows(BizException.class,
                () -> service.updateRole(11L, 22L, "ADMIN"));

        assertEquals("不能移除最后一个超级管理员", exception.getMessage());
        verify(userMapper, never()).updateById(any(User.class));
        verify(userRoleService, never()).replaceRoles(any(), anyList());
    }

    @Test
    void rejectsDisablingTheLastSuperAdmin() {
        when(userRoleService.hasAnyRole(22L, UserRoleService.SUPER_ADMIN)).thenReturn(true);
        when(userRoleService.hasAnyRole(11L, UserRoleService.SUPER_ADMIN)).thenReturn(true);
        when(userRoleService.lockUserIdsByRole(UserRoleService.SUPER_ADMIN)).thenReturn(List.of(22L));
        when(userMapper.selectById(22L)).thenReturn(user(22L));

        BizException exception = assertThrows(BizException.class,
                () -> service.updateStatus(11L, 22L, 0));

        assertEquals("不能禁用最后一个超级管理员", exception.getMessage());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void ordinaryAdminCannotGrantSuperAdminRole() {
        when(userRoleService.hasAnyRole(11L, UserRoleService.SUPER_ADMIN)).thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> service.updateRole(11L, 22L, "SUPER_ADMIN"));

        assertEquals(403, exception.getCode());
        verify(userMapper, never()).selectById(22L);
    }

    @Test
    void roleAndStatusChangesInvalidateExistingSessions() {
        when(userRoleService.hasAnyRole(11L, UserRoleService.SUPER_ADMIN)).thenReturn(true);
        when(userRoleService.getRoleCodes(22L)).thenReturn(List.of(UserRoleService.USER));
        when(userMapper.selectById(22L)).thenReturn(user(22L));
        when(adminViewMapper.toAdminUserListItemVO(any(User.class)))
                .thenAnswer(invocation -> new com.sakana.just_because_meme_understands_you.api.admin.vo.AdminUserListItemVO());
        when(ossUrlHelper.toPublicUrl(null)).thenReturn(null);

        service.updateRole(11L, 22L, "ADMIN");

        verify(userRoleService).replaceRoles(22L, List.of(UserRoleService.USER, UserRoleService.ADMIN));
        verify(userSessionRevoker).revokeAllByUserId("22");
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setStatus(1);
        return user;
    }
}
