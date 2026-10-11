package com.sakana.just_because_meme_understands_you.modules.ai.application;

import com.sakana.just_because_meme_understands_you.api.admin.vo.AiIngestBackfillVO;

/**
 * 将 MySQL 已发布梗同步到 MemeAgent / pgvector。
 */
public interface IAiIngestService {

    /** 判断 AI Agent 当前是否具备索引投递配置。 */
    boolean isAvailable();

    /**
     * 同步单条已发布梗（status=1）；非公开则删向量。
     */
    boolean syncMeme(Long memeId);

    /** 同步投递梗图当前状态并等待 MemeAgent 确认；失败时抛出异常供 Outbox 重试。 */
    void synchronizeMemeIndex(Long memeId);

    /**
     * 从向量库移除梗（下架 / 彻底删除）。
     */
    void removeMeme(Long memeId);

    /**
     * 回填全部已发布梗。
     */
    AiIngestBackfillVO backfillPublished(Integer limit);
}
