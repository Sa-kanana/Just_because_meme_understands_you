package com.sakana.just_because_meme_understands_you.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

@Configuration
public class OssConfig {

    @Value("${oss.endpoint}")
    private String endpoint;

    @Value("${oss.accessKeyId}")
    private String accessKeyId;

    @Value("${oss.accessKeySecret}")
    private String accessKeySecret;

    private OSS ossClient;

    @Bean
    public OSS ossClient() {
        ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        return ossClient;
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}
