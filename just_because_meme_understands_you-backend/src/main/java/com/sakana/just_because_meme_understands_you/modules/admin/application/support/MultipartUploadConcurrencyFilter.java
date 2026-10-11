package com.sakana.just_because_meme_understands_you.modules.admin.application.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** 在 Spring 解析 multipart 请求体前限制上传并发，避免内存暂存无限增长。 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MultipartUploadConcurrencyFilter extends OncePerRequestFilter {

    private final Semaphore permits;
    private final KnowledgeUploadProperties properties;
    private final ObjectMapper objectMapper;

    public MultipartUploadConcurrencyFilter(
            KnowledgeUploadProperties properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.permits = new Semaphore(properties.getMaxConcurrentRequests(), true);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType == null
                || !contentType.toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        long waitStarted = System.nanoTime();
        boolean acquired;
        try {
            acquired = permits.tryAcquire(properties.getAdmissionWaitMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writeBusyResponse(response, "文件上传排队已中断，请稍后重试");
            return;
        }
        long waitedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - waitStarted);
        if (!acquired) {
            writeBusyResponse(response, "文件上传繁忙，请稍后重试");
            log.warn("Multipart upload admission timed out wait_ms={} active={}",
                    waitedMs, properties.getMaxConcurrentRequests() - permits.availablePermits());
            return;
        }
        try {
            log.info("Multipart upload admitted wait_ms={} active={}",
                    waitedMs, properties.getMaxConcurrentRequests() - permits.availablePermits());
            filterChain.doFilter(request, response);
        } finally {
            permits.release();
        }
    }

    private void writeBusyResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(503, message));
    }
}
