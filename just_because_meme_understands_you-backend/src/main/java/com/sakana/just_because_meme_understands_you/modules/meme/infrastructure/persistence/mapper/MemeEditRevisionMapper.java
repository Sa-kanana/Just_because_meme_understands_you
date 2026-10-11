package com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeEditRevision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/** 中文注释：梗修改稿审核记录持久化映射。 */
@Mapper
public interface MemeEditRevisionMapper extends BaseMapper<MemeEditRevision> {

    /** 中文注释：按梗ID批量读取每条梗最近一次修改稿，避免个人主页逐条查询。 */
    @Select("<script>"
            + "SELECT ranked.id, ranked.meme_id, ranked.editor_user_id, ranked.base_content_hash, "
            + "ranked.content_json, ranked.status, ranked.review_note, ranked.reviewed_by, "
            + "ranked.created_at, ranked.reviewed_at "
            + "FROM (SELECT revision.*, ROW_NUMBER() OVER (PARTITION BY revision.meme_id "
            + "ORDER BY revision.created_at DESC, revision.id DESC) AS revision_rank "
            + "FROM meme_edit_revision revision WHERE revision.meme_id IN "
            + "<foreach collection='memeIds' item='memeId' open='(' separator=',' close=')'>"
            + "#{memeId}</foreach>) ranked WHERE ranked.revision_rank = 1"
            + "</script>")
    @Results(id = "memeEditRevisionResult", value = {
            @Result(column = "id", property = "id", id = true),
            @Result(column = "meme_id", property = "memeId"),
            @Result(column = "editor_user_id", property = "editorUserId"),
            @Result(column = "base_content_hash", property = "baseContentHash"),
            @Result(column = "content_json", property = "contentJson"),
            @Result(column = "status", property = "status"),
            @Result(column = "review_note", property = "reviewNote"),
            @Result(column = "reviewed_by", property = "reviewedBy"),
            @Result(column = "created_at", property = "createdAt"),
            @Result(column = "reviewed_at", property = "reviewedAt")
    })
    List<MemeEditRevision> selectLatestByMemeIds(@Param("memeIds") Collection<Long> memeIds);
}
