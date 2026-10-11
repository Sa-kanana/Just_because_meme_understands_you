package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.admin.application.assembler.AdminViewMapper;
import com.sakana.just_because_meme_understands_you.modules.auth.application.UserSessionRevoker;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.modules.user.application.UserRoleService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserDeletionTest {

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

    @Mock
    private IUserProfileService userProfileService;

    @InjectMocks
    private AdminUserServiceImpl service;

    @Test
    void softDeleteKeepsTheUserRecordAndRevokesSessionsAndProfileCaches() {
        when(userRoleService.hasAnyRole(11L, UserRoleService.SUPER_ADMIN)).thenReturn(false);
        when(userRoleService.getRoleCodes(22L)).thenReturn(List.of(UserRoleService.USER));
        when(userMapper.selectById(22L)).thenReturn(user(22L, null));
        when(adminViewMapper.toAdminUserListItemVO(any(User.class)))
                .thenAnswer(invocation -> new com.sakana.just_because_meme_understands_you.api.admin.vo.AdminUserListItemVO());
        when(ossUrlHelper.toPublicUrl(null)).thenReturn(null);

        service.delete(11L, 22L);

        ArgumentCaptor<User> updatedUser = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(updatedUser.capture());
        assertNotNull(updatedUser.getValue().getDeletedAt());
        verify(userSessionRevoker).revokeAllByUserId("22");
        verify(userProfileService).evictUserCache(22L);
    }

    @Test
    void ordinaryAdminCannotDeleteAnAdminAccount() {
        when(userRoleService.hasAnyRole(11L, UserRoleService.SUPER_ADMIN)).thenReturn(false);
        when(userRoleService.getRoleCodes(22L)).thenReturn(List.of(UserRoleService.ADMIN, UserRoleService.USER));
        when(userMapper.selectById(22L)).thenReturn(user(22L, null));

        BizException exception = assertThrows(BizException.class, () -> service.delete(11L, 22L));

        assertEquals(403, exception.getCode());
        verify(userMapper, never()).updateById(any(User.class));
        verify(userSessionRevoker, never()).revokeAllByUserId("22");
    }

    @Test
    void cannotDeleteTheCurrentAdminAccount() {
        BizException exception = assertThrows(BizException.class, () -> service.delete(11L, 11L));

        assertEquals("不能删除当前登录账号", exception.getMessage());
        verify(userMapper, never()).selectById(11L);
    }

    @Test
    void cannotDeleteTheLastNonDeletedSuperAdmin() {
        when(userRoleService.hasAnyRole(11L, UserRoleService.SUPER_ADMIN)).thenReturn(true);
        when(userRoleService.getRoleCodes(22L)).thenReturn(List.of(UserRoleService.SUPER_ADMIN, UserRoleService.USER));
        when(userRoleService.lockNotDeletedUserIdsByRole(UserRoleService.SUPER_ADMIN)).thenReturn(List.of(22L));
        when(userMapper.selectById(22L)).thenReturn(user(22L, null));

        BizException exception = assertThrows(BizException.class, () -> service.delete(11L, 22L));

        assertEquals("不能删除最后一个超级管理员", exception.getMessage());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void onlySuperAdminCanQueryDeletedUsers() {
        when(userRoleService.hasAnyRole(11L, UserRoleService.SUPER_ADMIN)).thenReturn(false);

        BizException exception = assertThrows(BizException.class,
                () -> service.page(11L, 1, 10, null, null, null, true));

        assertEquals(403, exception.getCode());
    }

    @Test
    void onlySuperAdminCanRestoreADeletedUser() {
        when(userRoleService.hasAnyRole(11L, UserRoleService.SUPER_ADMIN)).thenReturn(false);

        BizException exception = assertThrows(BizException.class, () -> service.restore(11L, 22L));

        assertEquals(403, exception.getCode());
        verify(userMapper, never()).selectById(22L);
    }

    @Test
    void superAdminCanRestoreAndCacheIsEvicted() {
        when(userRoleService.hasAnyRole(11L, UserRoleService.SUPER_ADMIN)).thenReturn(true);
        when(userMapper.selectById(22L)).thenReturn(user(22L, java.time.LocalDateTime.now()));
        when(userMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);
        when(userRoleService.getRoleCodes(22L)).thenReturn(List.of(UserRoleService.USER));
        when(adminViewMapper.toAdminUserListItemVO(any(User.class)))
                .thenAnswer(invocation -> new com.sakana.just_because_meme_understands_you.api.admin.vo.AdminUserListItemVO());
        when(ossUrlHelper.toPublicUrl(null)).thenReturn(null);

        service.restore(11L, 22L);

        ArgumentCaptor<Wrapper<User>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).update(isNull(), wrapperCaptor.capture());
        UpdateWrapper<User> updateWrapper = (UpdateWrapper<User>) wrapperCaptor.getValue();
        assertTrue(updateWrapper.getSqlSet().contains("deleted_at"));
        assertTrue(updateWrapper.getParamNameValuePairs().containsValue(null));
        verify(userProfileService).evictUserCache(22L);
    }

    private User user(Long id, java.time.LocalDateTime deletedAt) {
        User user = new User();
        user.setId(id);
        user.setNickname("梗友");
        user.setStatus(1);
        user.setDeletedAt(deletedAt);
        return user;
    }
}
