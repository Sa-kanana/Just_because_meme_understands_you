package com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.config;

import com.sakana.just_because_meme_understands_you.modules.moderation.application.SensitiveWordFilterService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SensitiveWordReloadListener implements MessageListener {

    private final SensitiveWordFilterService sensitiveWordFilterService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        sensitiveWordFilterService.reload();
    }
}
