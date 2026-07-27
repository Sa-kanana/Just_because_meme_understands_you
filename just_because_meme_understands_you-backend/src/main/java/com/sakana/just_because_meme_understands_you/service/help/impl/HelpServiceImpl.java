package com.sakana.just_because_meme_understands_you.service.help.impl;

import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.service.help.IHelpService;
import com.sakana.just_because_meme_understands_you.vo.HelpDocVO;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 从 classpath 加载帮助文档 Markdown。
 */
@Slf4j
@Service
public class HelpServiceImpl implements IHelpService {

    private static final String RESOURCE_PATH = "help/Help.md";
    private static final String DEFAULT_TITLE = "只因「梗」懂你 · 帮助中心";

    private volatile HelpDocVO cached;

    @PostConstruct
    void warmCache() {
        try {
            cached = loadFromClasspath();
        } catch (Exception e) {
            log.warn("帮助文档预加载失败，将在首次请求时重试: {}", e.getMessage());
        }
    }

    @Override
    public HelpDocVO getDoc() {
        HelpDocVO local = cached;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (cached == null) {
                cached = loadFromClasspath();
            }
            return cached;
        }
    }

    private HelpDocVO loadFromClasspath() {
        try {
            ClassPathResource resource = new ClassPathResource(RESOURCE_PATH);
            if (!resource.exists()) {
                throw new BizException(Result.CODE_NOT_FOUND, "帮助文档暂不可用");
            }
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            if (!StringUtils.hasText(content)) {
                throw new BizException(Result.CODE_NOT_FOUND, "帮助文档暂不可用");
            }
            String normalized = content.replace("\r\n", "\n").trim() + "\n";
            return HelpDocVO.builder()
                    .title(extractTitle(normalized))
                    .content(normalized)
                    .version(sha256Short(normalized))
                    .build();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取帮助文档失败", e);
            throw new BizException(Result.CODE_ERROR, "帮助文档加载失败");
        }
    }

    private static String extractTitle(String markdown) {
        for (String line : markdown.split("\n", 8)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("# ")) {
                String title = trimmed.substring(2).trim();
                if (StringUtils.hasText(title)) {
                    return title;
                }
            }
        }
        return DEFAULT_TITLE;
    }

    private static String sha256Short(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }
}
