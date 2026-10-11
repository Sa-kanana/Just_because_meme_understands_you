package com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AdminKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminKnowledgeMapper extends BaseMapper<AdminKnowledge> {

    /** 插入新正文；唯一指纹已存在时安全忽略，由调用方读取并复用赢家记录。 */
    @Insert("INSERT IGNORE INTO admin_knowledge "
            + "(id, title, content, category, tags, content_hash, content_sha256, index_status, index_revision, "
            + "status, creator_id, create_time, update_time) "
            + "VALUES (#{document.id}, #{document.title}, #{document.content}, #{document.category}, #{document.tags}, "
            + "#{document.contentHash}, #{document.contentSha256}, #{document.indexStatus}, #{document.indexRevision}, "
            + "#{document.status}, #{document.creatorId}, #{document.createTime}, #{document.updateTime})")
    int insertIfContentSha256Absent(@Param("document") AdminKnowledge document);

    /** 通过唯一正文指纹加锁读取，避免并发上传绕过应用层去重。 */
    @Select("SELECT * FROM admin_knowledge WHERE content_sha256 = #{contentSha256} LIMIT 1 FOR UPDATE")
    AdminKnowledge selectByContentSha256ForUpdate(@Param("contentSha256") String contentSha256);

    /** 锁定知识文档行，串行化同一文档的并发编辑、删除和重新索引。 */
    @Select("SELECT * FROM admin_knowledge WHERE id = #{id} LIMIT 1 FOR UPDATE")
    AdminKnowledge selectByIdForUpdate(@Param("id") long id);

    /** 只允许当前版本更新索引状态，避免旧任务覆盖新任务状态。 */
    @Update("UPDATE admin_knowledge SET index_status = #{status}, index_error = #{error} "
            + "WHERE id = #{id} AND index_revision = #{revision}")
    int updateIndexStateIfRevision(@Param("id") long id,
                                   @Param("revision") long revision,
                                   @Param("status") String status,
                                   @Param("error") String error);

    /** 领取知识索引任务时保留所有终态，避免过期 worker 重置 READY/DELETED/FAILED。 */
    @Update("UPDATE admin_knowledge SET index_status = #{status}, index_error = NULL "
            + "WHERE id = #{id} AND index_revision = #{revision} "
            + "AND index_status NOT IN ('READY', 'DELETED', 'FAILED')")
    int claimIndexStateIfRevision(@Param("id") long id,
                                  @Param("revision") long revision,
                                  @Param("status") String status);

    /** 重试或最终失败不能覆盖已由其他 worker 确认的终态。 */
    @Update("UPDATE admin_knowledge SET index_status = #{status}, index_error = #{error} "
            + "WHERE id = #{id} AND index_revision = #{revision} "
            + "AND index_status NOT IN ('READY', 'DELETED', 'FAILED')")
    int updateIndexStateIfRevisionUnlessTerminal(@Param("id") long id,
                                                 @Param("revision") long revision,
                                                 @Param("status") String status,
                                                 @Param("error") String error);
}
