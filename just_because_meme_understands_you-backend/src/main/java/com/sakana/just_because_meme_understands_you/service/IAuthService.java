package com.sakana.just_because_meme_understands_you.service;

import com.sakana.just_because_meme_understands_you.dto.LoginRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.RegisterRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.RegisterResponseVO;
import com.sakana.just_because_meme_understands_you.dto.ResetPasswordRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.AuthTokenBundleVO;
import com.sakana.just_because_meme_understands_you.vo.SendCodeResponseVO;
import com.sakana.just_because_meme_understands_you.vo.VerifyCodeResponseVO;

public interface IAuthService {

    /**
     * 登录逻辑：根据 email + loginType 定位 user_auth，使用 BCrypt 校验密码，
     * 聚合 user 信息，生成 JWT 并返回。
     */
    AuthTokenBundleVO login(LoginRequestDTO request);

    /**
     * 登录自动续航：校验旧 JWT 后签发新 JWT 并返回最新用户信息。
     */
    AuthTokenBundleVO renewLogin(String refreshToken);

    /**
     * 注册逻辑：
     * 1. 校验参数与两次密码一致性
     * 2. 校验邮箱验证码（Redis）
     * 3. 查重（邮箱是否已注册）
     * 4. 保存 user 与 user_auth
     * 5. 删除验证码缓存
     */
    RegisterResponseVO register(RegisterRequestDTO request);

    /**
     * 发送注册验证码逻辑：
     * 1. 校验邮箱格式
     * 2. 查重（邮箱是否已注册）
     * 3. Redis 限流，限制 60s 内重复发送
     * 4. 生成 6 位数字验证码并写入 Redis（5-10 分钟过期）
     * 5. 通过 QQ 邮箱 SMTP 发送验证码邮件
     */
    SendCodeResponseVO sendRegisterCode(String email);

    // ---------- 忘记密码三步流程 ----------

    /**
     * 第一步：请求重置（发邮件）
     * 校验邮箱已注册，Redis 存 forgot_password:email -> code 过期 5 分钟，发邮件
     */
    SendCodeResponseVO sendForgotPasswordCode(String email);

    /**
     * 第二步：验证验证码
     * 核验通过后生成临时 reset_token，Redis 存 reset_token:{token} -> userId 过期 10 分钟，返回 token
     */
    VerifyCodeResponseVO verifyForgotPasswordCode(String email, String code);

    /**
     * 第三步：执行重置
     * 根据 token 从 Redis 取 userId，BCrypt 更新 user_auth 密码，并使该用户当前 JWT 失效
     */
    void resetPassword(ResetPasswordRequestDTO request);

    /**
     * 退出登录：删除 refreshToken（Redis）并将 accessToken 加入黑名单（Redis）。
     */
    void logout(String accessToken, String refreshToken);
}

