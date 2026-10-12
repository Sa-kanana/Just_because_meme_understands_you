package com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.config;

import com.sakana.just_because_meme_understands_you.modules.moderation.application.SensitiveWordFilterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class SensitiveWordReloadListener implements MessageListener {

    private final SensitiveWordFilterService sensitiveWordFilterService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8).trim();
        try {
            long version = Long.parseLong(payload);
            if (version > sensitiveWordFilterService.getLoadedVersion()) {
                sensitiveWordFilterService.reloadIfVersionChanged();
            }
        } catch (NumberFormatException ignored) {
            // 中文注释：兼容旧版发送的非版本通知，收到后直接从数据库重载词库。
            sensitiveWordFilterService.reload();
        }
    }
}
