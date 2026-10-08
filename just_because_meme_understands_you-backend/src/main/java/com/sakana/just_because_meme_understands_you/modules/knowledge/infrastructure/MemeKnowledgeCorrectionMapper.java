package com.sakana.just_because_meme_understands_you.modules.knowledge.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.knowledge.domain.MemeKnowledgeCorrection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import java.time.LocalDateTime;

@Mapper
public interface MemeKnowledgeCorrectionMapper extends BaseMapper<MemeKnowledgeCorrection> {
    @Update("""
        UPDATE meme_knowledge_correction SET status=#{decision}, handled_by=#{adminId},
        handling_note=#{note}, handled_at=#{handledAt}
        WHERE id=#{id} AND status='PENDING'
        """)
    int resolvePending(@Param("id") Long id, @Param("decision") String decision,
                       @Param("adminId") Long adminId, @Param("note") String note,
                       @Param("handledAt") LocalDateTime handledAt);
}
