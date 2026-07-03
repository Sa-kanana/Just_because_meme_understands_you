package com.sakana.just_because_meme_understands_you.config;

import com.sakana.just_because_meme_understands_you.service.comment.SensitiveWordFilterService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisMessageListenerConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            SensitiveWordReloadListener sensitiveWordReloadListener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(
                sensitiveWordReloadListener,
                new ChannelTopic(SensitiveWordFilterService.RELOAD_CHANNEL)
        );
        return container;
    }
}
