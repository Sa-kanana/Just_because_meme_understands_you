package com.sakana.just_because_meme_understands_you.service.user;

import com.sakana.just_because_meme_understands_you.dto.ChangePasswordRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.AccountSecurityVO;
import com.sakana.just_because_meme_understands_you.vo.AccountSettingsVO;
import com.sakana.just_because_meme_understands_you.vo.ChangePasswordResultVO;
import com.sakana.just_because_meme_understands_you.vo.RevokeSessionsResultVO;

/**
 * 账号设置：聚合资料/安全态、登录态改密、踢出全部设备。
 */
public interface IAccountSettingsService {

    AccountSettingsVO getSettings(Long userId, String currentRefreshToken);

    AccountSecurityVO getSecurity(Long userId, String currentRefreshToken);

    ChangePasswordResultVO changePassword(Long userId, ChangePasswordRequestDTO request);

    RevokeSessionsResultVO revokeAllSessions(Long userId);
}
