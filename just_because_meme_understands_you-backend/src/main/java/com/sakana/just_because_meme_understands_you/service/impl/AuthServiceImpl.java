package com.sakana.just_because_meme_understands_you.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.config.JwtUtil;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.entity.UserAuth;
import com.sakana.just_because_meme_understands_you.service.IAuthService;
import com.sakana.just_because_meme_understands_you.service.IUserAuthService;
import com.sakana.just_because_meme_understands_you.service.IUserService;
import com.sakana.just_because_meme_understands_you.util.DigestUtil;
import com.sakana.just_because_meme_understands_you.util.EmailValidatorUtil;
import com.sakana.just_because_meme_understands_you.dto.LoginRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.AuthTokenBundleVO;
import com.sakana.just_because_meme_understands_you.vo.LoginUserVO;
import com.sakana.just_because_meme_understands_you.dto.RegisterRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.RegisterResponseVO;
import com.sakana.just_because_meme_understands_you.dto.ResetPasswordRequestDTO;
import com.sakana.just_because_meme_understands_you.vo.SendCodeResponseVO;
import com.sakana.just_because_meme_understands_you.vo.VerifyCodeResponseVO;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements IAuthService {

    @Resource
    private IUserAuthService userAuthService;

    @Resource
    private IUserService userService;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String REGISTER_CODE_KEY_PREFIX = "register:code:";
    private static final String REGISTER_CODE_RATE_LIMIT_PREFIX = "register:code:rate:";
    private static final long REGISTER_CODE_TTL_MINUTES = 5L;
    private static final long REGISTER_CODE_RATE_LIMIT_SECONDS = 60L;

    /** 忘记密码：验证码 Redis key 前缀，key: forgot_password:email, value: code, expire: 5min */
    private static final String FORGOT_PASSWORD_CODE_PREFIX = "forgot_password:";
    private static final long FORGOT_PASSWORD_CODE_TTL_MINUTES = 5L;
    private static final String FORGOT_PASSWORD_RATE_PREFIX = "forgot_password:rate:";
    /** 重置令牌 Redis key 前缀，key: reset_token:{token}, value: userId, expire: 10min */
    private static final String RESET_TOKEN_PREFIX = "reset_token:";
    private static final long RESET_TOKEN_TTL_MINUTES = 10L;
    private static final String REFRESH_TOKEN_PREFIX = "auth:refresh:";
    private static final String ACCESS_BLACKLIST_PREFIX = "auth:blacklist:access:";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String LOGIN_TYPE_CLAIM = "loginType";
    private static final String ROLE_CLAIM = "role";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";
    private static final long BLACKLIST_MIN_TTL_MILLIS = 1000L;

    @Resource
    private JavaMailSender mailSender;

    /**
     * 发件人邮箱地址，必须与授权用户一致，避免 QQ SMTP 501 报错
     */
    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${jwt.expiration}")
    private long accessTokenExpirationMillis;

    @Value("${jwt.refresh-expiration}")
    private long refreshTokenExpirationMillis;

    @Transactional
    @Override
    public AuthTokenBundleVO login(LoginRequestDTO request) {
        String email = request.getEmail();
        String rawPassword = request.getPassword();
        String loginType = request.getLoginType();

        if (!StringUtils.hasText(email) || !StringUtils.hasText(rawPassword) || !StringUtils.hasText(loginType)) {
            throw new BizException(Result.CODE_ERROR, "请求参数不完整");
        }

        LambdaQueryWrapper<UserAuth> wrapper = new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getIdentityType, loginType)
                .eq(UserAuth::getIdentifier, email)
                .last("LIMIT 1");
        UserAuth userAuth = userAuthService.getOne(wrapper, false);
        if (userAuth == null) {
            throw new BizException(Result.CODE_ERROR, "账号或密码错误");
        }

        if (!passwordEncoder.matches(rawPassword, userAuth.getCredential())) {
            throw new BizException(Result.CODE_ERROR, "账号或密码错误");
        }

        User user = userService.getById(userAuth.getUserId());
        if (user == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }

        // 状态为 0 视为禁用；避免类型不匹配导致失效，这里统一以字符串比较
        if ("0".equals(String.valueOf(user.getStatus()))) {
            throw new BizException(Result.CODE_ERROR, "账号已被禁用");
        }

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
        } catch (Exception e) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "刷新令牌已过期，请重新登录");
        }
        if (!TOKEN_TYPE_REFRESH.equals(String.valueOf(claims.get(TOKEN_TYPE_CLAIM)))) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "无效的刷新令牌，请重新登录");
        }

        String userIdStr = claims.getSubject();
        if (!StringUtils.hasText(userIdStr)) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "无效的刷新令牌，请重新登录");
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "无效的刷新令牌，请重新登录");
        }

        String refreshKey = REFRESH_TOKEN_PREFIX + DigestUtil.md5Hex(refreshToken);
        String cachedUserId = stringRedisTemplate.opsForValue().getAndDelete(refreshKey);
        if (!StringUtils.hasText(cachedUserId) || !userIdStr.equals(cachedUserId)) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "刷新令牌已失效，请重新登录");
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new BizException(Result.CODE_REFRESH_TOKEN_EXPIRED, "用户不存在或会话已失效，请重新登录");
        }
        if ("0".equals(String.valueOf(user.getStatus()))) {
            throw new BizException(Result.CODE_ERROR, "账号已被禁用");
        }

        String loginType = String.valueOf(claims.get(LOGIN_TYPE_CLAIM));
        if (!StringUtils.hasText(loginType) || "null".equals(loginType)) {
            loginType = "email";
        }

        String newAccessToken = generateAccessToken(user, loginType);
        String newRefreshToken = generateRefreshToken(userIdStr, loginType);
        storeRefreshToken(newRefreshToken, userIdStr);
        return buildTokenBundle(newAccessToken, newRefreshToken, user);
    }

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

        // 校验邮箱是否已经注册
        LambdaQueryWrapper<UserAuth> existWrapper = new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getIdentityType, "email")
                .eq(UserAuth::getIdentifier, email)
                .last("LIMIT 1");
        UserAuth existed = userAuthService.getOne(existWrapper, false);
        if (existed != null) {
            throw new BizException(Result.CODE_ERROR, "该邮箱已被注册");
        }

        // 校验验证码
        String redisKey = REGISTER_CODE_KEY_PREFIX + email;
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

        // 创建用户认证信息（主键由 MyBatis-Plus 雪花算法自动填充）
        UserAuth userAuth = new UserAuth();
        userAuth.setUserId(userId);
        userAuth.setIdentityType("email");
        userAuth.setIdentifier(email);
        userAuth.setCredential(passwordEncoder.encode(password));
        userAuthService.save(userAuth);

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
        if (!StringUtils.hasText(email)) {
            throw new BizException(Result.CODE_ERROR, "邮箱不能为空");
        }

        // 宽松但规范的邮箱格式校验，避免误伤合法邮箱
        if (!EmailValidatorUtil.isValidEmail(email)) {
            throw new BizException(Result.CODE_ERROR, "邮箱格式不正确");
        }

        // 已注册用户不允许重复发送注册验证码
        LambdaQueryWrapper<UserAuth> existWrapper = new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getIdentityType, "email")
                .eq(UserAuth::getIdentifier, email)
                .last("LIMIT 1");
        UserAuth existed = userAuthService.getOne(existWrapper, false);
        if (existed != null) {
            throw new BizException(Result.CODE_ERROR, "该邮箱已被注册");
        }

        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();

        // 频率限制：同一个邮箱 60 秒内只允许发送一次
        String rateKey = REGISTER_CODE_RATE_LIMIT_PREFIX + email;
        Long expireSeconds = stringRedisTemplate.getExpire(rateKey, TimeUnit.SECONDS);
        if (expireSeconds != null && expireSeconds > 0) {
            SendCodeResponseVO rateLimitVO = new SendCodeResponseVO();
            rateLimitVO.setRetryAfter(expireSeconds);
            return rateLimitVO;
        }

        // 生成 6 位数字验证码
        String code = String.format("%06d", new Random().nextInt(1000000));

        // 写入 Redis：验证码 10 分钟过期
        String codeKey = REGISTER_CODE_KEY_PREFIX + email;
        ops.set(Objects.requireNonNull(codeKey), Objects.requireNonNull(code), REGISTER_CODE_TTL_MINUTES, TimeUnit.MINUTES);

        // 写入 Redis：发送频率限制 60 秒
        ops.set(Objects.requireNonNull(rateKey), "1", REGISTER_CODE_RATE_LIMIT_SECONDS, TimeUnit.SECONDS);

        // 发送邮件
        SimpleMailMessage message = new SimpleMailMessage();
        // 根据配置文件显式设置发件人，需与 spring.mail.username 相同
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("【Just Because Meme Understands You】注册验证码");
        message.setText("您的验证码为：" + code + "，有效期 " + REGISTER_CODE_TTL_MINUTES + " 分钟，请勿泄露给他人。");
        try {
            mailSender.send(message);
        } catch (org.springframework.mail.MailException e) {
            // 邮件发送失败时清理验证码与限流键，避免用户无法重试
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(rateKey);
            throw new BizException(Result.CODE_ERROR, "验证码发送失败，请稍后重试");
        }

        SendCodeResponseVO responseVO = new SendCodeResponseVO();
        responseVO.setRetryAfter(REGISTER_CODE_RATE_LIMIT_SECONDS);
        return responseVO;
    }

    // ---------- 忘记密码 ----------

    @Override
    public SendCodeResponseVO sendForgotPasswordCode(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BizException(Result.CODE_ERROR, "邮箱不能为空");
        }
        if (!EmailValidatorUtil.isValidEmail(email)) {
            throw new BizException(Result.CODE_ERROR, "邮箱格式不正确");
        }

        // 必须已注册才允许找回密码
        LambdaQueryWrapper<UserAuth> wrapper = new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getIdentityType, "email")
                .eq(UserAuth::getIdentifier, email)
                .last("LIMIT 1");
        UserAuth userAuth = userAuthService.getOne(wrapper, false);
        if (userAuth == null) {
            throw new BizException(Result.CODE_ERROR, "该邮箱未注册");
        }

        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();
        String rateKey = FORGOT_PASSWORD_RATE_PREFIX + email;
        Long expireSeconds = stringRedisTemplate.getExpire(rateKey, TimeUnit.SECONDS);
        if (expireSeconds != null && expireSeconds > 0) {
            SendCodeResponseVO rateLimitVO = new SendCodeResponseVO();
            rateLimitVO.setRetryAfter(expireSeconds);
            return rateLimitVO;
        }

        String code = String.format("%06d", new Random().nextInt(1000000));
        String codeKey = FORGOT_PASSWORD_CODE_PREFIX + email;
        ops.set(Objects.requireNonNull(codeKey), Objects.requireNonNull(code), FORGOT_PASSWORD_CODE_TTL_MINUTES, TimeUnit.MINUTES);
        ops.set(Objects.requireNonNull(rateKey), "1", REGISTER_CODE_RATE_LIMIT_SECONDS, TimeUnit.SECONDS);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(email);
        message.setSubject("【Just Because Meme Understands You】重置密码验证码");
        message.setText("您的验证码为：" + code + "，有效期 " + FORGOT_PASSWORD_CODE_TTL_MINUTES + " 分钟，请勿泄露。");
        try {
            mailSender.send(message);
        } catch (org.springframework.mail.MailException e) {
            stringRedisTemplate.delete(codeKey);
            stringRedisTemplate.delete(rateKey);
            throw new BizException(Result.CODE_ERROR, "验证码发送失败，请稍后重试");
        }

        SendCodeResponseVO responseVO = new SendCodeResponseVO();
        responseVO.setRetryAfter(REGISTER_CODE_RATE_LIMIT_SECONDS);
        return responseVO;
    }

    @Override
    public VerifyCodeResponseVO verifyForgotPasswordCode(String email, String code) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(code)) {
            throw new BizException(Result.CODE_ERROR, "邮箱和验证码不能为空");
        }

        String codeKey = FORGOT_PASSWORD_CODE_PREFIX + email;
        String cachedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (!StringUtils.hasText(cachedCode) || !code.trim().equals(cachedCode)) {
            throw new BizException(Result.CODE_ERROR, "验证码错误或已过期");
        }

        LambdaQueryWrapper<UserAuth> wrapper = new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getIdentityType, "email")
                .eq(UserAuth::getIdentifier, email)
                .last("LIMIT 1");
        UserAuth userAuth = userAuthService.getOne(wrapper, false);
        if (userAuth == null) {
            stringRedisTemplate.delete(codeKey);
            throw new BizException(Result.CODE_ERROR, "用户不存在");
        }

        String resetToken = UUID.randomUUID().toString();
        String resetKey = RESET_TOKEN_PREFIX + resetToken;
        stringRedisTemplate.opsForValue().set(Objects.requireNonNull(resetKey), Objects.requireNonNull(String.valueOf(userAuth.getUserId())), RESET_TOKEN_TTL_MINUTES, TimeUnit.MINUTES);
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

        String resetKey = RESET_TOKEN_PREFIX + token;
        String userIdStr = stringRedisTemplate.opsForValue().get(resetKey);
        if (!StringUtils.hasText(userIdStr)) {
            throw new BizException(Result.CODE_ERROR, "重置链接已过期，请重新获取验证码");
        }

        long userId = Long.parseLong(userIdStr);
        LambdaQueryWrapper<UserAuth> wrapper = new LambdaQueryWrapper<UserAuth>()
                .eq(UserAuth::getUserId, userId)
                .eq(UserAuth::getIdentityType, "email")
                .last("LIMIT 1");
        UserAuth userAuth = userAuthService.getOne(wrapper, false);
        if (userAuth == null) {
            stringRedisTemplate.delete(resetKey);
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }

        userAuth.setCredential(passwordEncoder.encode(newPassword));
        userAuthService.updateById(userAuth);

        stringRedisTemplate.delete(resetKey);

    }

    @Override
    public void logout(String accessToken, String refreshToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new BizException(Result.CODE_ERROR, "未携带有效令牌");
        }
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtUtil.parseToken(accessToken);
        } catch (Exception e) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "无效的令牌，请重新登录");
        }
        if (!TOKEN_TYPE_ACCESS.equals(String.valueOf(claims.get(TOKEN_TYPE_CLAIM)))) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "无效的访问令牌");
        }

        String blacklistKey = ACCESS_BLACKLIST_PREFIX + DigestUtil.md5Hex(accessToken);
        long ttlMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (ttlMillis < BLACKLIST_MIN_TTL_MILLIS) {
            ttlMillis = BLACKLIST_MIN_TTL_MILLIS;
        }
        stringRedisTemplate.opsForValue().set(
                Objects.requireNonNull(blacklistKey),
                Objects.requireNonNull(String.valueOf(claims.getSubject())),
                ttlMillis,
                TimeUnit.MILLISECONDS
        );

        if (StringUtils.hasText(refreshToken)) {
            String refreshKey = REFRESH_TOKEN_PREFIX + DigestUtil.md5Hex(refreshToken);
            stringRedisTemplate.delete(refreshKey);
        }
    }

    private String generateAccessToken(User user, String loginType) {
        Map<String, Object> claims = new HashMap<>(4);
        claims.put(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS);
        claims.put(ROLE_CLAIM, user.getRole());
        claims.put(LOGIN_TYPE_CLAIM, loginType);
        return jwtUtil.generateToken(String.valueOf(user.getId()), claims, accessTokenExpirationMillis);
    }

    private String generateRefreshToken(String userId, String loginType) {
        Map<String, Object> claims = new HashMap<>(4);
        claims.put(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH);
        claims.put(LOGIN_TYPE_CLAIM, loginType);
        claims.put("tokenId", UUID.randomUUID().toString());
        return jwtUtil.generateToken(userId, claims, refreshTokenExpirationMillis);
    }

    private void storeRefreshToken(String refreshToken, String userId) {
        String refreshKey = REFRESH_TOKEN_PREFIX + DigestUtil.md5Hex(refreshToken);
        stringRedisTemplate.opsForValue().set(
                Objects.requireNonNull(refreshKey),
                Objects.requireNonNull(userId),
                refreshTokenExpirationMillis,
                TimeUnit.MILLISECONDS
        );
    }

    private AuthTokenBundleVO buildTokenBundle(String accessToken, String refreshToken, User user) {
        LoginUserVO userVO = new LoginUserVO();
        userVO.setId(user.getId());
        userVO.setNickname(user.getNickname());
        userVO.setAvatar(user.getAvatar());
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

