package com.sakana.just_because_meme_understands_you.service.auth.impl;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.constant.AuthConstants;
import com.sakana.just_because_meme_understands_you.config.JwtUtil;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.entity.UserAuth;
import com.sakana.just_because_meme_understands_you.mapper.UserAuthMapper;
import com.sakana.just_because_meme_understands_you.service.auth.IAuthService;
import com.sakana.just_because_meme_understands_you.service.auth.UserSessionRevoker;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.service.user.IUserService;
import com.sakana.just_because_meme_understands_you.util.DigestUtil;
import com.sakana.just_because_meme_understands_you.util.EmailValidatorUtil;
import com.sakana.just_because_meme_understands_you.util.PasswordPolicy;
import com.sakana.just_because_meme_understands_you.dto.LoginRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.RegisterRequestDTO;
import com.sakana.just_because_meme_understands_you.dto.ResetPasswordRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.AuthTokenBundleVO;
import com.sakana.just_because_meme_understands_you.vo.LoginUserVO;
import com.sakana.just_because_meme_understands_you.vo.RegisterResponseVO;
import com.sakana.just_because_meme_understands_you.vo.SendCodeResponseVO;
import com.sakana.just_because_meme_understands_you.vo.VerifyCodeResponseVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现：登录、注册、忘记密码、退出登录。
 *
 * @author sakana
 */
@Slf4j
@Service
public class AuthServiceImpl implements IAuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Resource
    private UserAuthMapper userAuthMapper;

    @Resource
    private IUserService userService;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private JavaMailSender mailSender;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Resource
    private UserSessionRevoker userSessionRevoker;

    /** 发件人邮箱地址，必须与授权用户一致，避免 QQ SMTP 501 报错 */
    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${jwt.expiration}")
    private long accessTokenExpirationMillis;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpirationMillis;

    // ==================== 登录 / 续航 ====================

    @Transactional
    @Override
    public AuthTokenBundleVO login(LoginRequestDTO request) {
        String email = request.getEmail();
        String rawPassword = request.getPassword();
        String loginType = request.getLoginType();

        if (!StringUtils.hasText(email) || !StringUtils.hasText(rawPassword) || !StringUtils.hasText(loginType)) {
            throw new BizException(Result.CODE_ERROR, "请求参数不完整");
        }

        UserAuth userAuth = userAuthMapper.selectByIdentity(loginType, email);
        if (userAuth == null || !passwordEncoder.matches(rawPassword, userAuth.getCredential())) {
            throw new BizException(Result.CODE_ERROR, "账号或密码错误");
        }

        User user = userService.getById(userAuth.getUserId());
        if (user == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }
        ensureUserActive(user);

        String userId = String.valueOf(user.getId());
        String accessToken = generateAccessToken(user, loginType);
        String refreshToken = generateRefreshToken(userId, loginType);
        storeRefreshToken(refreshToken, userId);
        return buildTokenBundle(accessToken, refreshToken, user);
    }

    @Override
    public AuthTokenBundleVO renewLogin(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "刷新令牌缺失或已过期，请重新登录");
        }

        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtUtil.parseToken(refreshToken);
        } catch (Exception ignored) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "刷新令牌已过期，请重新登录");
        }
        if (!AuthConstants.TOKEN_TYPE_REFRESH.equals(String.valueOf(claims.get(AuthConstants.CLAIM_TOKEN_TYPE)))) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "无效的刷新令牌，请重新登录");
        }

        String userIdStr = claims.getSubject();
        if (!StringUtils.hasText(userIdStr)) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "无效的刷新令牌，请重新登录");
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException ignored) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "无效的刷新令牌，请重新登录");
        }

        String refreshKey = AuthConstants.REFRESH_TOKEN_PREFIX + DigestUtil.md5Hex(refreshToken);
        String cachedUserId = stringRedisTemplate.opsForValue().getAndDelete(refreshKey);
        if (!StringUtils.hasText(cachedUserId) || !userIdStr.equals(cachedUserId)) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "刷新令牌已失效，请重新登录");
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "用户不存在或会话已失效，请重新登录");
        }
        ensureUserActive(user);

        String loginType = String.valueOf(claims.get(AuthConstants.CLAIM_LOGIN_TYPE));
        if (!StringUtils.hasText(loginType) || "null".equals(loginType)) {
            loginType = AuthConstants.LOGIN_TYPE_EMAIL;
        }

        String newAccessToken = generateAccessToken(user, loginType);
        String newRefreshToken = generateRefreshToken(userIdStr, loginType);
        storeRefreshToken(newRefreshToken, userIdStr);
        return buildTokenBundle(newAccessToken, newRefreshToken, user);
    }

    // ==================== 注册 ====================

    @Transactional
    @Override
    public RegisterResponseVO register(RegisterRequestDTO request) {
        String email = request.getEmail();
        String password = request.getPassword();
        String confirmPassword = request.getConfirmPassword();
        String verificationCode = request.getVerificationCode();
        String nickname = request.getNickname();

        if (!StringUtils.hasText(email)
                || !StringUtils.hasText(password)
                || !StringUtils.hasText(confirmPassword)
                || !StringUtils.hasText(verificationCode)
                || !StringUtils.hasText(nickname)) {
            throw new BizException(Result.CODE_ERROR, "请求参数不完整");
        }
        if (!password.equals(confirmPassword)) {
            throw new BizException(Result.CODE_ERROR, "两次密码输入不一致");
        }
        PasswordPolicy.validate(password);

        // 校验邮箱是否已注册
        if (userAuthMapper.selectByIdentity(AuthConstants.LOGIN_TYPE_EMAIL, email) != null) {
            throw new BizException(Result.CODE_ERROR, "该邮箱已被注册");
        }

        // 校验验证码
        String redisKey = AuthConstants.REGISTER_CODE_PREFIX + email;
        String cachedCode = stringRedisTemplate.opsForValue().get(redisKey);
        if (!StringUtils.hasText(cachedCode) || !verificationCode.equals(cachedCode)) {
            throw new BizException(Result.CODE_ERROR, "验证码错误或已过期");
        }

        // 创建用户（主键由 MyBatis-Plus 雪花算法自动填充）
        User user = new User();
        user.setNickname(nickname);
        user.setRole("ROLE_USER");
        user.setStatus(1);
        userService.save(user);
        long userId = user.getId();

        // 创建用户认证信息
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(userId);
        userAuth.setIdentityType(AuthConstants.LOGIN_TYPE_EMAIL);
        userAuth.setIdentifier(email);
        userAuth.setCredential(passwordEncoder.encode(password));
        userAuthMapper.insert(userAuth);

        // 注册成功后删除验证码
        stringRedisTemplate.delete(redisKey);

        RegisterResponseVO responseVO = new RegisterResponseVO();
        responseVO.setUserId(userId);
        responseVO.setEmail(email);
        responseVO.setNickname(nickname);
        responseVO.setCreatedAt(java.time.Instant.now().toString());
        return responseVO;
    }

    @Override
    public SendCodeResponseVO sendRegisterCode(String email) {
        validateEmailFormat(email);
        // 已注册用户不允许重复发送注册验证码
        if (userAuthMapper.selectByIdentity(AuthConstants.LOGIN_TYPE_EMAIL, email) != null) {
            throw new BizException(Result.CODE_ERROR, "该邮箱已被注册");
        }
        return sendVerificationCode(
                email,
                AuthConstants.REGISTER_CODE_PREFIX,
                AuthConstants.REGISTER_CODE_RATE_PREFIX,
                AuthConstants.REGISTER_CODE_TTL_MINUTES,
                "【Just Because Meme Understands You】注册验证码"
        );
    }

    // ==================== 忘记密码 ====================

    @Override
    public SendCodeResponseVO sendForgotPasswordCode(String email) {
        validateEmailFormat(email);
        // 必须已注册才允许找回密码
        if (userAuthMapper.selectByIdentity(AuthConstants.LOGIN_TYPE_EMAIL, email) == null) {
            throw new BizException(Result.CODE_ERROR, "该邮箱未注册");
        }
        return sendVerificationCode(
                email,
                AuthConstants.FORGOT_PASSWORD_CODE_PREFIX,
                AuthConstants.FORGOT_PASSWORD_RATE_PREFIX,
                AuthConstants.FORGOT_PASSWORD_CODE_TTL_MINUTES,
                "【Just Because Meme Understands You】重置密码验证码"
        );
    }

    @Override
    public VerifyCodeResponseVO verifyForgotPasswordCode(String email, String code) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(code)) {
            throw new BizException(Result.CODE_ERROR, "邮箱和验证码不能为空");
        }

        String codeKey = AuthConstants.FORGOT_PASSWORD_CODE_PREFIX + email;
        String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (!StringUtils.hasText(cachedCode) || !code.trim().equals(cachedCode)) {
            throw new BizException(Result.CODE_ERROR, "验证码错误或已过期");
        }

        UserAuth userAuth = userAuthMapper.selectByIdentity(AuthConstants.LOGIN_TYPE_EMAIL, email);
        if (userAuth == null) {
            stringRedisTemplate.delete(codeKey);
            throw new BizException(Result.CODE_ERROR, "用户不存在");
        }

        String resetToken = UUID.randomUUID().toString();
        String resetKey = AuthConstants.RESET_TOKEN_PREFIX + resetToken;
        stringRedisTemplate.opsForValue().set(
                Objects.requireNonNull(resetKey),
                Objects.requireNonNull(String.valueOf(userAuth.getUserId())),
                AuthConstants.RESET_TOKEN_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        stringRedisTemplate.delete(codeKey);

        VerifyCodeResponseVO vo = new VerifyCodeResponseVO();
        vo.setToken(resetToken);
        return vo;
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequestDTO request) {
        String token = request.getToken();
        String newPassword = request.getNewPassword();
        if (!StringUtils.hasText(token) || !StringUtils.hasText(newPassword)) {
            throw new BizException(Result.CODE_ERROR, "token 和新密码不能为空");
        }
        PasswordPolicy.validate(newPassword);

        String resetKey = AuthConstants.RESET_TOKEN_PREFIX + token;
        String userIdStr = stringRedisTemplate.opsForValue().get(resetKey);
        if (!StringUtils.hasText(userIdStr)) {
            throw new BizException(Result.CODE_ERROR, "重置链接已过期，请重新获取验证码");
        }

        long userId = Long.parseLong(userIdStr);
        UserAuth userAuth = userAuthMapper.selectByUserIdAndType(userId, AuthConstants.LOGIN_TYPE_EMAIL);
        if (userAuth == null) {
            stringRedisTemplate.delete(resetKey);
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }

        userAuth.setCredential(passwordEncoder.encode(newPassword));
        userAuthMapper.updateById(userAuth);
        stringRedisTemplate.delete(resetKey);
        userSessionRevoker.revokeAllByUserId(userIdStr);
    }

    // ==================== 退出登录 ====================

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new BizException(Result.CODE_ERROR, "未携带有效令牌");
        }
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtUtil.parseToken(accessToken);
        } catch (Exception ignored) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "无效的令牌，请重新登录");
        }
        if (!AuthConstants.TOKEN_TYPE_ACCESS.equals(String.valueOf(claims.get(AuthConstants.CLAIM_TOKEN_TYPE)))) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "无效的访问令牌");
        }

        String blacklistKey = AuthConstants.ACCESS_BLACKLIST_PREFIX + DigestUtil.md5Hex(accessToken);
        long ttlMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (ttlMillis < AuthConstants.BLACKLIST_MIN_TTL_MILLIS) {
            ttlMillis = AuthConstants.BLACKLIST_MIN_TTL_MILLIS;
        }
        stringRedisTemplate.opsForValue().set(
                Objects.requireNonNull(blacklistKey),
                Objects.requireNonNull(String.valueOf(claims.getSubject())),
                ttlMillis,
                TimeUnit.MILLISECONDS
        );

        if (StringUtils.hasText(refreshToken)) {
            userSessionRevoker.revokeRefreshToken(refreshToken, String.valueOf(claims.getSubject()));
        }
    }

    // ==================== 私有辅助 ====================

    /**
     * 校验用户状态，status 为 0 视为禁用。
     */
    private void ensureUserActive(User user) {
        if ("0".equals(String.valueOf(user.getStatus()))) {
            throw new BizException(Result.CODE_ERROR, "账号已被禁用");
        }
    }

    /**
     * 校验邮箱非空且格式合法。
     */
    private void validateEmailFormat(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BizException(Result.CODE_ERROR, "邮箱不能为空");
        }
        if (!EmailValidatorUtil.isValidEmail(email)) {
            throw new BizException(Result.CODE_ERROR, "邮箱格式不正确");
        }
    }

    /**
     * 通用发送验证码流程：频率限制 -> 生成 -> 写 Redis -> 发邮件，失败回滚 Redis。
     * 合并注册验证码与忘记密码验证码的重复逻辑。
     *
     * @param email           邮箱
     * @param codePrefix      验证码 Redis key 前缀
     * @param ratePrefix      频率限制 Redis key 前缀
     * @param ttlMinutes      验证码有效期（分钟）
     * @param mailSubject     邮件主题
     * @return 发送结果，含下次可发送的倒计时
     */
    private SendCodeResponseVO sendVerificationCode(String email, String codePrefix, String ratePrefix,
                                                    long ttlMinutes, String mailSubject) {
        String rateKey = ratePrefix + email;
        Long expireSeconds = stringRedisTemplate.getExpire(rateKey, TimeUnit.SECONDS);
        if (expireSeconds != null && expireSeconds > 0) {
            SendCodeResponseVO rateLimitVO = new SendCodeResponseVO();
            rateLimitVO.setRetryAfter(expireSeconds);
            return rateLimitVO;
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));
        String codeKey = codePrefix + email;
        stringRedisTemplate.opsForValue().set(codeKey, code, ttlMinutes, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(rateKey, "1", AuthConstants.CODE_RATE_LIMIT_SECONDS, TimeUnit.SECONDS);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject(mailSubject);
        message.setText("您的验证码为：" + code + "，有效期 " + ttlMinutes + " 分钟，请勿泄露给他人。");
        try {
            mailSender.send(message);
        } catch (org.springframework.mail.MailException mailEx) {
            log.warn("验证码邮件发送失败, email={}", email, mailEx);
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(rateKey);
            throw new BizException(Result.CODE_ERROR, "验证码发送失败，请稍后重试");
        }

        SendCodeResponseVO responseVO = new SendCodeResponseVO();
        responseVO.setRetryAfter(AuthConstants.CODE_RATE_LIMIT_SECONDS);
        return responseVO;
    }

    private String generateAccessToken(User user, String loginType) {
        Map<String, Object> claims = new HashMap<>(4);
        claims.put(AuthConstants.CLAIM_TOKEN_TYPE, AuthConstants.TOKEN_TYPE_ACCESS);
        claims.put(AuthConstants.CLAIM_ROLE, user.getRole());
        claims.put(AuthConstants.CLAIM_LOGIN_TYPE, loginType);
        return jwtUtil.generateToken(String.valueOf(user.getId()), claims, accessTokenExpirationMillis);
    }

    private String generateRefreshToken(String userId, String loginType) {
        Map<String, Object> claims = new HashMap<>(4);
        claims.put(AuthConstants.CLAIM_TOKEN_TYPE, AuthConstants.TOKEN_TYPE_REFRESH);
        claims.put(AuthConstants.CLAIM_LOGIN_TYPE, loginType);
        claims.put("tokenId", UUID.randomUUID().toString());
        return jwtUtil.generateToken(userId, claims, refreshTokenExpirationMillis);
    }

    private void storeRefreshToken(String refreshToken, String userId) {
        String refreshMd5 = DigestUtil.md5Hex(refreshToken);
        String refreshKey = AuthConstants.REFRESH_TOKEN_PREFIX + refreshMd5;
        String userIndexKey = AuthConstants.USER_REFRESH_INDEX_PREFIX + userId;

        String previousMd5 = stringRedisTemplate.opsForValue().get(userIndexKey);
        if (StringUtils.hasText(previousMd5) && !previousMd5.equals(refreshMd5)) {
            stringRedisTemplate.delete(AuthConstants.REFRESH_TOKEN_PREFIX + previousMd5);
        }

        stringRedisTemplate.opsForValue().set(
                refreshKey,
                userId,
                refreshTokenExpirationMillis,
                TimeUnit.MILLISECONDS
        );
        stringRedisTemplate.opsForValue().set(
                userIndexKey,
                refreshMd5,
                refreshTokenExpirationMillis,
                TimeUnit.MILLISECONDS
        );
    }

    private AuthTokenBundleVO buildTokenBundle(String accessToken, String refreshToken, User user) {
        LoginUserVO userVO = new LoginUserVO();
        userVO.setId(user.getId());
        userVO.setNickname(user.getNickname());
        userVO.setAvatar(ossUrlHelper.toPublicUrl(user.getAvatar()));
        userVO.setSignature(user.getSignature());
        userVO.setRole(user.getRole());
        userVO.setStatus(user.getStatus());

        AuthTokenBundleVO bundleVO = new AuthTokenBundleVO();
        bundleVO.setAccessToken(accessToken);
        bundleVO.setRefreshToken(refreshToken);
        bundleVO.setUser(userVO);
        return bundleVO;
    }
}
