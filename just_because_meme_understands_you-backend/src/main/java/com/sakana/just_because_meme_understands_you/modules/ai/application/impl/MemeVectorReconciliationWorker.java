package com.sakana.just_because_meme_understands_you.modules.ai.application.impl;

import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.MemeAgentClient;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto.MemeAgentMemeIdPageDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.MemeVectorReconciliationJob;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.MemeVectorReconciliationJobMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeIndexEventPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 只处理管理员创建任务的单页校准 worker。 */
@Slf4j
@Component
public class MemeVectorReconciliationWorker {

    /** 中文注释：每次 Java 目录读取最多处理 50 个梗 ID。 */
    static final int PUBLISHED_PAGE_SIZE = 50;
    /** 中文注释：每次向量库 ID 清理最多读取 500 个梗 ID。 */
    static final int VECTOR_PAGE_SIZE = 500;
    /** 中文注释：跨实例任务租约为 60 秒。 */
    static final long LEASE_SECONDS = 60L;
    /** 中文注释：worker 每秒检查一次是否有活动任务，不会自行创建校准任务。 */
    static final long POLL_INTERVAL_MS = 1000L;

    private final MemeVectorReconciliationJobMapper jobMapper;
    private final MemeCatalogPort memeCatalogPort;
    private final MemeAgentClient memeAgentClient;
    private final MemeIndexEventPort memeIndexEventPort;
    private final TransactionTemplate transactionTemplate;
    /** 中文注释：当前 JVM 的 worker 标识，租约依靠该值避免其他实例重复提交游标。 */
    private final String workerId = UUID.randomUUID().toString();

    public MemeVectorReconciliationWorker(
            MemeVectorReconciliationJobMapper jobMapper,
            MemeCatalogPort memeCatalogPort,
            MemeAgentClient memeAgentClient,
            MemeIndexEventPort memeIndexEventPort,
            TransactionTemplate transactionTemplate) {
        this.jobMapper = jobMapper;
        this.memeCatalogPort = memeCatalogPort;
        this.memeAgentClient = memeAgentClient;
        this.memeIndexEventPort = memeIndexEventPort;
        this.transactionTemplate = transactionTemplate;
    }

    /** 轮询并处理一个已存在的活动任务页面。 */
    @Scheduled(fixedDelayString = "${ai.vector-reconciliation.poll-interval-ms:1000}")
    public void processOnePage() {
        MemeVectorReconciliationJob job = leaseNextJob();
        if (job == null) {
            return;
        }

        long startedAt = System.nanoTime();
        String stage = job.getPhase();
        try {
            if ("SYNC_PUBLISHED".equals(stage)) {
                processPublishedPage(job);
            } else if ("CLEAN_ORPHAN".equals(stage)) {
                processVectorPage(job);
            } else {
                throw new IllegalStateException("unknown phase");
            }
            log.info("Meme vector reconciliation page completed jobId={} phase={} cursor={} elapsed_ms={}",
                    job.getId(), stage, cursorForLog(job), (System.nanoTime() - startedAt) / 1_000_000L);
        } catch (Exception exception) {
            markFailed(job, stage);
            log.warn("Meme vector reconciliation page failed jobId={} phase={} cursor={} errorType={} elapsed_ms={}",
                    job.getId(), stage, cursorForLog(job), exception.getClass().getSimpleName(),
                    (System.nanoTime() - startedAt) / 1_000_000L);
        }
    }

    private MemeVectorReconciliationJob leaseNextJob() {
        return transactionTemplate.execute(status -> {
            MemeVectorReconciliationJob job = jobMapper.selectNextRunnableForUpdate();
            if (job == null || job.getId() == null) {
                return null;
            }
            if (jobMapper.lease(job.getId(), workerId, leaseUntil()) != 1) {
                return null;
            }
            job.setStatus("RUNNING");
            job.setLeaseOwner(workerId);
            return job;
        });
    }

    private void processPublishedPage(MemeVectorReconciliationJob job) {
        long cursor = job.getPublishedCursor() == null ? 0L : job.getPublishedCursor();
        List<Long> ids = memeCatalogPort.pagePublishedIdsAfterId(cursor, PUBLISHED_PAGE_SIZE);
        validatePublishedIds(ids, cursor);
        if (ids.isEmpty()) {
            inTransaction(() -> requireOne(jobMapper.moveToOrphanCleanup(
                    job.getId(), workerId, leaseUntil())));
            return;
        }

        long nextCursor = ids.get(ids.size() - 1);
        inTransaction(() -> {
            ids.forEach(memeIndexEventPort::requestUpsert);
            requireOne(jobMapper.advancePublishedPage(
                    job.getId(), workerId, nextCursor, ids.size(), leaseUntil()));
        });
    }

    private void processVectorPage(MemeVectorReconciliationJob job) {
        String cursor = job.getVectorCursor();
        MemeAgentMemeIdPageDTO page = memeAgentClient.listMemeIdsPage(cursor, VECTOR_PAGE_SIZE)
                .block(Duration.ofSeconds(30));
        if (page == null || page.getMemeIds() == null) {
            throw new IllegalStateException("invalid vector page");
        }
        List<Long> vectorIds = parseVectorIds(page.getMemeIds(), cursor);
        if (vectorIds.size() > VECTOR_PAGE_SIZE) {
            throw new IllegalStateException("vector page exceeded limit");
        }
        String nextCursor = page.getNextCursor();
        validateVectorCursor(cursor, nextCursor, vectorIds);

        Set<Long> publishedIds = findPublishedIds(vectorIds);
        List<Long> orphanIds = vectorIds.stream().filter(id -> !publishedIds.contains(id)).toList();
        boolean lastPage = nextCursor == null;
        inTransaction(() -> {
            orphanIds.forEach(memeIndexEventPort::requestRemoval);
            if (lastPage) {
                requireOne(jobMapper.completeVectorPage(
                        job.getId(), workerId, vectorIds.size(), orphanIds.size()));
            } else {
                requireOne(jobMapper.advanceVectorPage(
                        job.getId(), workerId, nextCursor, vectorIds.size(), orphanIds.size(), leaseUntil()));
            }
        });
    }

    private Set<Long> findPublishedIds(List<Long> vectorIds) {
        if (vectorIds.isEmpty()) {
            return Set.of();
        }
        List<MemeCatalogPort.MemeCard> published = memeCatalogPort.findPublishedByIds(vectorIds);
        if (published == null) {
            throw new IllegalStateException("published status lookup failed");
        }
        Set<Long> ids = new HashSet<>();
        for (MemeCatalogPort.MemeCard meme : published) {
            if (meme != null && meme.id() != null && Integer.valueOf(1).equals(meme.status())) {
                ids.add(meme.id());
            }
        }
        return ids;
    }

    private void markFailed(MemeVectorReconciliationJob job, String stage) {
        try {
            inTransaction(() -> jobMapper.markFailed(job.getId(), workerId, safeFailureStage(stage)));
        } catch (RuntimeException persistenceFailure) {
            log.error("Meme vector reconciliation failure state persistence failed jobId={} phase={} errorType={}",
                    job.getId(), stage, persistenceFailure.getClass().getSimpleName());
        }
    }

    private static String safeFailureStage(String stage) {
        return "SYNC_PUBLISHED".equals(stage) || "CLEAN_ORPHAN".equals(stage) ? stage : "UNKNOWN_PHASE";
    }

    private static void validatePublishedIds(List<Long> ids, long cursor) {
        if (ids == null || ids.size() > PUBLISHED_PAGE_SIZE) {
            throw new IllegalStateException("invalid published ID page");
        }
        long previous = cursor;
        for (Long id : ids) {
            if (id == null || id <= previous) {
                throw new IllegalStateException("published ID page is not increasing");
            }
            previous = id;
        }
    }

    private static List<Long> parseVectorIds(List<String> ids, String cursor) {
        List<Long> parsed = new java.util.ArrayList<>(ids.size());
        long previous = cursor == null ? 0L : parsePositiveId(cursor);
        for (String value : ids) {
            long id = parsePositiveId(value);
            if (id <= previous) {
                throw new IllegalStateException("vector ID page is not increasing");
            }
            parsed.add(id);
            previous = id;
        }
        return List.copyOf(parsed);
    }

    private static void validateVectorCursor(String cursor, String nextCursor, List<Long> ids) {
        if (nextCursor == null) {
            return;
        }
        long next = parsePositiveId(nextCursor);
        long previous = cursor == null ? 0L : parsePositiveId(cursor);
        if (next <= previous || (!ids.isEmpty() && next != ids.get(ids.size() - 1))) {
            throw new IllegalStateException("invalid vector next cursor");
        }
    }

    private static long parsePositiveId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // 中文注释：错误内容只进入安全失败阶段，不记录外部服务返回的原始 ID 字符串。
        }
        throw new IllegalStateException("invalid vector ID");
    }

    private void inTransaction(Runnable action) {
        transactionTemplate.execute(status -> {
            action.run();
            return null;
        });
    }

    private static void requireOne(int updatedRows) {
        if (updatedRows != 1) {
            throw new IllegalStateException("job lease lost");
        }
    }

    private static LocalDateTime leaseUntil() {
        return LocalDateTime.now().plusSeconds(LEASE_SECONDS);
    }

    private static String cursorForLog(MemeVectorReconciliationJob job) {
        return "SYNC_PUBLISHED".equals(job.getPhase())
                ? String.valueOf(job.getPublishedCursor())
                : String.valueOf(job.getVectorCursor());
    }
}
