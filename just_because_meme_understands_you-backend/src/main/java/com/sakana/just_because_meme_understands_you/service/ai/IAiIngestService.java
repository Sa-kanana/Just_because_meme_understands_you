package com.sakana.just_because_meme_understands_you.service.ai;

import com.sakana.just_because_meme_understands_you.vo.AiIngestBackfillVO;

/**
 * 将 MySQL 已发布梗同步到 MemeAgent / pgvector。
 */
public interface IAiIngestService {

    /**
     * 同步单条已发布梗（status=1）；非公开则删向量。
     */
    boolean syncMeme(Long memeId);

    /**
     * 从向量库移除梗（下架 / 彻底删除）。
     */
    void removeMeme(Long memeId);

    /**
     * 回填全部已发布梗。
     */
    AiIngestBackfillVO backfillPublished(Integer limit);
}
