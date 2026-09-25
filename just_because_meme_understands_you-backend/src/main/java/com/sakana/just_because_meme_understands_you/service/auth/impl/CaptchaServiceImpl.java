package com.sakana.just_because_meme_understands_you.service.auth.impl;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.constant.AuthConstants;
import com.sakana.just_because_meme_understands_you.service.auth.ICaptchaService;
import com.sakana.just_because_meme_understands_you.vo.CaptchaResponseVO;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 自建图形验证码：四位易读字符 + Redis 一次性核销。
 */
@Service
public class CaptchaServiceImpl implements ICaptchaService {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 排除易混字符 0/O、1/I/l */
    private static final char[] CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 4;
    private static final int IMAGE_WIDTH = 132;
    private static final int IMAGE_HEIGHT = 44;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public CaptchaResponseVO create() {
        String code = randomCode();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String redisKey = AuthConstants.CAPTCHA_PREFIX + captchaId;
        stringRedisTemplate.opsForValue().set(
                redisKey,
                code,
                AuthConstants.CAPTCHA_TTL_SECONDS,
                TimeUnit.SECONDS
        );

        CaptchaResponseVO vo = new CaptchaResponseVO();
        vo.setCaptchaId(captchaId);
        vo.setImageBase64(renderBase64Png(code));
        vo.setExpireSeconds(AuthConstants.CAPTCHA_TTL_SECONDS);
        return vo;
    }

    @Override
    public void verifyAndConsume(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请完成人机验证");
        }
        String id = captchaId.trim();
        String input = captchaCode.trim().toUpperCase(Locale.ROOT);
        String redisKey = AuthConstants.CAPTCHA_PREFIX + id;
        String cached = stringRedisTemplate.opsForValue().get(redisKey);
        stringRedisTemplate.delete(redisKey);
        if (!StringUtils.hasText(cached) || !cached.equalsIgnoreCase(input)) {
            throw new BizException(Result.CODE_ERROR, "人机验证失败，请刷新后重试");
        }
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
        }
        return sb.toString();
    }

    private static String renderBase64Png(String code) {
        //创建画布
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            //填充背景 + 抗锯齿
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
            //干扰线（防 OCR）
            for (int i = 0; i < 6; i++) {
                g.setColor(randomNoiseColor(160, 210));
                int x1 = RANDOM.nextInt(IMAGE_WIDTH);
                int y1 = RANDOM.nextInt(IMAGE_HEIGHT);
                int x2 = RANDOM.nextInt(IMAGE_WIDTH);
                int y2 = RANDOM.nextInt(IMAGE_HEIGHT);
                g.drawLine(x1, y1, x2, y2);
            }
            //干扰点（防 OCR）
            for (int i = 0; i < 28; i++) {
                g.setColor(randomNoiseColor(140, 220));
                g.fillOval(RANDOM.nextInt(IMAGE_WIDTH), RANDOM.nextInt(IMAGE_HEIGHT), 2, 2);
            }
            //绘制验证码字符
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            int charWidth = IMAGE_WIDTH / (CODE_LENGTH + 1);// 每个字符的宽度区间
            for (int i = 0; i < code.length(); i++) {
                // 随机颜色（深色，确保可读
                g.setColor(new Color(40 + RANDOM.nextInt(60), 50 + RANDOM.nextInt(70), 90 + RANDOM.nextInt(80)));
                // 随机旋转角度（-0.225 ~ +0.225 弧度）
                double angle = (RANDOM.nextDouble() - 0.5) * 0.45;
                // 计算位置：均匀分布 + 随机偏移
                int x = charWidth * (i + 1) - 8;
                int y = 30 + RANDOM.nextInt(6);
                g.rotate(angle, x, y);// 旋转画布
                g.drawString(String.valueOf(code.charAt(i)), x, y);// 绘制字符
                g.rotate(-angle, x, y);// 旋转画布，恢复原始角度
            }
        } finally {
            g.dispose();
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new BizException(Result.CODE_ERROR, "验证码生成失败，请稍后重试");
        }
    }

    private static Color randomNoiseColor(int min, int max) {
        int span = Math.max(1, max - min);
        return new Color(min + RANDOM.nextInt(span), min + RANDOM.nextInt(span), min + RANDOM.nextInt(span));
    }
}
