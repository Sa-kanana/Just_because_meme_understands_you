package com.sakana.just_because_meme_understands_you.modules.user.application;

import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.RoleMapper;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserRoleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserRoleServiceTest {

    @Mock
    private RoleMapper roleMapper;

    @Mock
    private UserRoleMapper userRoleMapper;

    @InjectMocks
    private UserRoleService userRoleService;

    @Test
    void recognizesCanonicalRolesAndLegacySpringAuthorities() {
        when(userRoleMapper.selectRoleCodesByUserId(17L)).thenReturn(List.of("USER", "ADMIN"));

        assertTrue(userRoleService.hasAnyRole(17L, "ROLE_ADMIN"));
        assertTrue(userRoleService.hasAnyRole(17L, "USER"));
        assertFalse(userRoleService.hasAnyRole(17L, "SUPER_ADMIN"));
    }

    @Test
    void roleCodesAreReturnedInStablePrivilegeOrder() {
        when(userRoleMapper.selectRoleCodesByUserId(17L)).thenReturn(List.of("USER", "SUPER_ADMIN", "ADMIN"));

        assertEquals(List.of("SUPER_ADMIN", "ADMIN", "USER"), userRoleService.getRoleCodes(17L));
    }

    @Test
    void rejectsUnsupportedRoleBeforeChangingAssociations() {
        assertThrows(IllegalArgumentException.class,
                () -> userRoleService.replaceRoles(17L, List.of("ROOT")));

        verifyNoInteractions(roleMapper, userRoleMapper);
    }
}
