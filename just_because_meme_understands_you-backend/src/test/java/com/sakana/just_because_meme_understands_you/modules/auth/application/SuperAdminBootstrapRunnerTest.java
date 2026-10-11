package com.sakana.just_because_meme_understands_you.modules.auth.application;

import com.sakana.just_because_meme_understands_you.modules.user.application.UserRoleService;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserAuth;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserAuthMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminBootstrapRunnerTest {

    @Mock
    private UserRoleService userRoleService;

    @Mock
    private UserAuthMapper userAuthMapper;

    @InjectMocks
    private SuperAdminBootstrapRunner runner;

    @Test
    void grantsSuperAdminToConfiguredExistingEmailOnlyWhenNoSuperAdminExists() throws Exception {
        ReflectionTestUtils.setField(runner, "bootstrapEmail", "root@example.com");
        when(userRoleService.countUsersByRole(UserRoleService.SUPER_ADMIN)).thenReturn(0L);
        UserAuth auth = new UserAuth();
        auth.setUserId(42L);
        when(userAuthMapper.selectByIdentity("email", "root@example.com")).thenReturn(auth);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(userRoleService).addRole(42L, UserRoleService.SUPER_ADMIN);
    }

    @Test
    void doesNothingWhenBootstrapEmailIsNotConfiguredOrSuperAdminAlreadyExists() throws Exception {
        ReflectionTestUtils.setField(runner, "bootstrapEmail", " ");

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(userRoleService, never()).countUsersByRole(UserRoleService.SUPER_ADMIN);
        verify(userAuthMapper, never()).selectByIdentity("email", " ");
    }

    @Test
    void failsClearlyWhenConfiguredBootstrapEmailDoesNotBelongToAnExistingUser() throws Exception {
        ReflectionTestUtils.setField(runner, "bootstrapEmail", "missing@example.com");
        when(userRoleService.countUsersByRole(UserRoleService.SUPER_ADMIN)).thenReturn(0L);
        when(userAuthMapper.selectByIdentity("email", "missing@example.com")).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0])));
    }
}
