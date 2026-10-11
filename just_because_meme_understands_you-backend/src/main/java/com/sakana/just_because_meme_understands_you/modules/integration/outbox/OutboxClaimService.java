package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 在短事务内锁定待投递事件，避免多个应用实例重复领取。 */
@Service
public class OutboxClaimService {

    // 中文注释：响应超时之外的缓冲时间，避免正常慢请求在完成前被重新领取。
    private static final long LEASE_MARGIN_SECONDS = 60L;
    // 中文注释：即使 Agent 响应很快，也至少保留五分钟租约。
    private static final long MIN_LEASE_SECONDS = 300L;

    private final OutboxEventMapper mapper;
    // 中文注释：读取 Agent HTTP 响应超时，使事件租约覆盖最长同步灌库请求。
    private final MemeAgentProperties memeAgentProperties;

    public OutboxClaimService(OutboxEventMapper mapper, MemeAgentProperties memeAgentProperties) {
        this.mapper = mapper;
        this.memeAgentProperties = memeAgentProperties;
    }

    @Transactional
    public List<OutboxEvent> claim(int limit) {
        // 中文注释：与 MySQL DATETIME(3) 的精度一致，后续用 locked_at 作租约所有权比较。
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        long responseTimeoutMs = Math.max(0L, memeAgentProperties.getAgent().getResponseTimeoutMs());
        long responseTimeoutSeconds = responseTimeoutMs / 1000L
                + (responseTimeoutMs % 1000L == 0 ? 0 : 1);
        long leaseSeconds = Math.max(
                MIN_LEASE_SECONDS,
                responseTimeoutSeconds + LEASE_MARGIN_SECONDS);
        List<OutboxEvent> events = mapper.selectClaimable(
                now, now.minusSeconds(leaseSeconds), limit);
        for (OutboxEvent event : events) {
            event.setStatus("PROCESSING");
            event.setLockedAt(now);
            mapper.updateById(event);
        }
        return events;
    }
}
