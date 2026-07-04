package com.sakana.just_because_meme_understands_you.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 评论等非核心链路使用的本地异步线程池（无 MQ 场景）。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "memeCommentExecutor")
    public Executor memeCommentExecutor(
            @Value("${meme-comment.async.core-pool-size:4}") int corePoolSize,
            @Value("${meme-comment.async.max-pool-size:16}") int maxPoolSize,
            @Value("${meme-comment.async.queue-capacity:500}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("meme-comment-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean(name = "memePublishExecutor")
    public Executor memePublishExecutor(
            @Value("${meme-publish.async.core-pool-size:2}") int corePoolSize,
            @Value("${meme-publish.async.max-pool-size:8}") int maxPoolSize,
            @Value("${meme-publish.async.queue-capacity:300}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("meme-publish-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
