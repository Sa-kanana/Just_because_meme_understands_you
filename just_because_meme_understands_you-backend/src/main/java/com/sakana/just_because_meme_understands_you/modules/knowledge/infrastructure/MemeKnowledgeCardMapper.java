package com.sakana.just_because_meme_understands_you.modules.knowledge.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.knowledge.domain.MemeKnowledgeCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface MemeKnowledgeCardMapper extends BaseMapper<MemeKnowledgeCard> {
    @Insert("""
        INSERT INTO meme_knowledge_card
        (meme_id, source_url, first_seen_date, popularity_context, usage_examples, evolution, related_meme_ids)
        VALUES (#{card.memeId}, #{card.sourceUrl}, #{card.firstSeenDate}, #{card.popularityContext},
                #{card.usageExamples}, #{card.evolution}, #{card.relatedMemeIds})
        ON DUPLICATE KEY UPDATE source_url=VALUES(source_url), first_seen_date=VALUES(first_seen_date),
            popularity_context=VALUES(popularity_context), usage_examples=VALUES(usage_examples),
            evolution=VALUES(evolution), related_meme_ids=VALUES(related_meme_ids)
        """)
    int upsert(@Param("card") MemeKnowledgeCard card);
}
