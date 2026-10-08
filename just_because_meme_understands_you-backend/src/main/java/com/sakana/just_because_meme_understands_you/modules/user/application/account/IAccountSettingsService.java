package com.sakana.just_because_meme_understands_you.modules.user.application.account;

import com.sakana.just_because_meme_understands_you.api.user.dto.ChangePasswordRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.AccountSecurityVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.AccountSettingsVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.ChangePasswordResultVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.RevokeSessionsResultVO;

/**
 * 账号设置：聚合资料/安全态、登录态改密、踢出全部设备。
 */
public interface IAccountSettingsService {

    AccountSettingsVO getSettings(Long userId, String currentRefreshToken);

    AccountSecurityVO getSecurity(Long userId, String currentRefreshToken);

    ChangePasswordResultVO changePassword(Long userId, ChangePasswordRequestDTO request);

    RevokeSessionsResultVO revokeAllSessions(Long userId);
}
