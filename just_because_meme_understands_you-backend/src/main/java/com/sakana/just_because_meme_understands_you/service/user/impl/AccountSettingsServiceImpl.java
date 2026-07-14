package com.sakana.just_because_meme_understands_you.service.user.impl;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.constant.AuthConstants;
import com.sakana.just_because_meme_understands_you.dto.ChangePasswordRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.entity.UserAuth;
import com.sakana.just_because_meme_understands_you.mapper.UserAuthMapper;
import com.sakana.just_because_meme_understands_you.service.auth.UserSessionRevoker;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.service.user.IAccountSettingsService;
import com.sakana.just_because_meme_understands_you.service.user.IUserService;
import com.sakana.just_because_meme_understands_you.util.EmailMaskUtil;
import com.sakana.just_because_meme_understands_you.util.PasswordPolicy;
import com.sakana.just_because_meme_understands_you.vo.AccountProfileVO;
import com.sakana.just_because_meme_understands_you.vo.AccountSecurityVO;
import com.sakana.just_because_meme_understands_you.vo.AccountSettingsVO;
import com.sakana.just_because_meme_understands_you.vo.ChangePasswordResultVO;
import com.sakana.just_because_meme_understands_you.vo.RevokeSessionsResultVO;
import jakarta.annotation.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class AccountSettingsServiceImpl implements IAccountSettingsService {

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Resource
    private IUserService userService;

    @Resource
    private UserAuthMapper userAuthMapper;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Resource
    private UserSessionRevoker userSessionRevoker;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public AccountSettingsVO getSettings(Long userId, String currentRefreshToken) {
        User user = requireUser(userId);
        UserAuth emailAuth = userAuthMapper.selectByUserIdAndType(userId, AuthConstants.LOGIN_TYPE_EMAIL);

        AccountSettingsVO vo = new AccountSettingsVO();
        vo.setProfile(toProfileVO(user));
        vo.setSecurity(toSecurityVO(userId, emailAuth, currentRefreshToken));
        return vo;
    }

    @Override
    public AccountSecurityVO getSecurity(Long userId, String currentRefreshToken) {
        requireUser(userId);
        UserAuth emailAuth = userAuthMapper.selectByUserIdAndType(userId, AuthConstants.LOGIN_TYPE_EMAIL);
        return toSecurityVO(userId, emailAuth, currentRefreshToken);
    }

    @Override
    @Transactional
    public ChangePasswordResultVO changePassword(Long userId, ChangePasswordRequestDTO request) {
        if (request == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请求参数不能为空");
        }
        String oldPassword = request.getOldPassword();
        String newPassword = request.getNewPassword();
        String confirmPassword = request.getConfirmPassword();
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword) || !StringUtils.hasText(confirmPassword)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请填写完整的密码信息");
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "两次输入的新密码不一致");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "新密码不能与旧密码相同");
        }
        PasswordPolicy.validate(newPassword);

        requireUser(userId);
        UserAuth userAuth = userAuthMapper.selectByUserIdAndType(userId, AuthConstants.LOGIN_TYPE_EMAIL);
        if (userAuth == null || !StringUtils.hasText(userAuth.getCredential())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "当前账号未设置密码，请使用邮箱验证码重置");
        }
        if (!passwordEncoder.matches(oldPassword, userAuth.getCredential())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "旧密码不正确");
        }

        userAuth.setCredential(passwordEncoder.encode(newPassword));
        userAuth.setPasswordChangedAt(LocalDateTime.now());
        userAuthMapper.updateById(userAuth);
        userSessionRevoker.revokeAllByUserId(String.valueOf(userId));

        ChangePasswordResultVO result = new ChangePasswordResultVO();
        result.setRequireReLogin(true);
        return result;
    }

    @Override
    public RevokeSessionsResultVO revokeAllSessions(Long userId) {
        requireUser(userId);
        int revoked = userSessionRevoker.revokeAllByUserId(String.valueOf(userId));
        RevokeSessionsResultVO result = new RevokeSessionsResultVO();
        result.setRevokedCount(revoked);
        return result;
    }

    private User requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
        User user = userService.getById(userId);
        if (user == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private AccountProfileVO toProfileVO(User user) {
        AccountProfileVO vo = new AccountProfileVO();
        vo.setUserId(String.valueOf(user.getId()));
        vo.setNickname(user.getNickname() == null ? "" : user.getNickname().trim());
        vo.setAvatar(ossUrlHelper.toPublicUrl(user.getAvatar()));
        if (vo.getAvatar() == null) {
            vo.setAvatar("");
        }
        vo.setSignature(user.getSignature() == null ? "" : user.getSignature().trim());
        vo.setGender(user.getGender() == null ? 0 : user.getGender());
        vo.setBirthday(user.getBirthday() == null ? "" : user.getBirthday().toString());
        return vo;
    }

    private AccountSecurityVO toSecurityVO(Long userId, UserAuth emailAuth, String currentRefreshToken) {
        AccountSecurityVO vo = new AccountSecurityVO();
        boolean emailBound = emailAuth != null && StringUtils.hasText(emailAuth.getIdentifier());
        vo.setEmailBound(emailBound);
        vo.setEmailMasked(emailBound ? EmailMaskUtil.mask(emailAuth.getIdentifier()) : "");
        boolean passwordSet = emailAuth != null && StringUtils.hasText(emailAuth.getCredential());
        vo.setPasswordSet(passwordSet);
        if (passwordSet && emailAuth.getPasswordChangedAt() != null) {
            vo.setLastPasswordChangeTime(emailAuth.getPasswordChangedAt().format(ISO_DATE_TIME));
        } else {
            vo.setLastPasswordChangeTime("");
        }
        vo.setHasOtherSessions(userSessionRevoker.hasOtherSessions(String.valueOf(userId), currentRefreshToken));
        return vo;
    }
}
