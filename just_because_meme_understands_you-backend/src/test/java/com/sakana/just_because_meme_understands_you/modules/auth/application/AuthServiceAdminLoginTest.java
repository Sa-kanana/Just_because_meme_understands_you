package com.sakana.just_because_meme_understands_you.modules.auth.application;

import com.sakana.just_because_meme_understands_you.api.user.dto.LoginRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.AuthTokenBundleVO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.auth.application.impl.AuthServiceImpl;
import com.sakana.just_because_meme_understands_you.modules.auth.infrastructure.config.JwtUtil;
import com.sakana.just_because_meme_understands_you.modules.user.application.UserRoleService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserService;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserAuth;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserAuthMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceAdminLoginTest {

    @Mock
    private UserAuthMapper userAuthMapper;

    @Mock
    private IUserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserSessionRevoker userSessionRevoker;

    @Mock
    private ICaptchaService captchaService;

    @Mock
    private UserRoleService userRoleService;

    @Mock
    private OssUrlHelper ossUrlHelper;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void configureTokenExpiry() {
        ReflectionTestUtils.setField(authService, "accessTokenExpirationMillis", 60000L);
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationMillis", 120000L);
    }

    @Test
    void adminLoginRejectsAValidUserAccountWithoutAdminRole() {
        stubCredentials(List.of("USER"));

        BizException exception = assertThrows(BizException.class,
                () -> authService.loginAdmin(loginRequest()));

        assertEquals(403, exception.getCode());
        assertEquals("该账号没有管理端权限", exception.getMessage());
        verify(jwtUtil, never()).generateToken(anyString(), anyMap(), anyLong());
        verify(userSessionRevoker, never()).registerSession(anyString(), anyString(), anyLong());
    }

    @Test
    void adminLoginAcceptsSuperAdminAndReturnsAllAssignedRoles() {
        stubCredentials(List.of("SUPER_ADMIN", "USER"));
        when(jwtUtil.generateToken(anyString(), anyMap(), anyLong()))
                .thenReturn("access-token", "refresh-token");
        when(userSessionRevoker.currentTokenVersion("42")).thenReturn(0L);

        AuthTokenBundleVO result = authService.loginAdmin(loginRequest());

        assertEquals("access-token", result.getAccessToken());
        assertEquals("ROLE_SUPER_ADMIN", result.getUser().getRole());
        assertEquals(List.of("SUPER_ADMIN", "USER"), result.getUser().getRoles());
        verify(userSessionRevoker).registerSession("refresh-token", "42", 120000L);
    }

    @Test
    void deletedAccountsCannotUseUserOrAdminLogin() {
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(42L);
        userAuth.setCredential("hashed-password");
        when(userAuthMapper.selectByIdentity("email", "admin@example.com")).thenReturn(userAuth);
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);
        User user = new User();
        user.setId(42L);
        user.setNickname("已注销账号");
        user.setStatus(1);
        user.setDeletedAt(java.time.LocalDateTime.now());
        when(userService.getById(42L)).thenReturn(user);

        BizException adminException = assertThrows(BizException.class,
                () -> authService.loginAdmin(loginRequest()));
        BizException userException = assertThrows(BizException.class,
                () -> authService.login(loginRequest()));

        assertEquals("账号已注销，无法登录", adminException.getMessage());
        assertEquals("账号已注销，无法登录", userException.getMessage());
        verify(jwtUtil, never()).generateToken(anyString(), anyMap(), anyLong());
        verify(userSessionRevoker, never()).registerSession(anyString(), anyString(), anyLong());
    }

    private void stubCredentials(List<String> roles) {
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(42L);
        userAuth.setCredential("hashed-password");
        when(userAuthMapper.selectByIdentity("email", "admin@example.com")).thenReturn(userAuth);
        when(passwordEncoder.matches("password", "hashed-password")).thenReturn(true);

        User user = new User();
        user.setId(42L);
        user.setNickname("Admin");
        user.setStatus(1);
        when(userService.getById(42L)).thenReturn(user);
        when(userRoleService.getRoleCodes(42L)).thenReturn(roles);
    }

    private LoginRequestDTO loginRequest() {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail("admin@example.com");
        request.setPassword("password");
        request.setLoginType("email");
        request.setCaptchaId("captcha-id");
        request.setCaptchaCode("a1b2");
        return request;
    }
}
