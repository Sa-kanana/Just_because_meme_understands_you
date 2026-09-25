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
        // ① 创建消息监听容器
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        // ② 设置 Redis 连接
        container.setConnectionFactory(connectionFactory);
        // ③ 注册监听器到指定频道
        container.addMessageListener(
                sensitiveWordReloadListener,
                new ChannelTopic(SensitiveWordFilterService.RELOAD_CHANNEL)
        );
        return container;
    }
}
