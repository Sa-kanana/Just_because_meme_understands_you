package com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper;

import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 梗 Mapper
 *
 * @author sakana
 * @since 2026-03-06
 */
@Mapper
public interface MemeMapper extends BaseMapper<Meme> {

    /** 在当前事务中锁定梗行，串行化审核、下架和恢复等状态变更。 */
    @Select("SELECT * FROM meme WHERE id = #{memeId} LIMIT 1 FOR UPDATE")
    Meme selectByIdForUpdate(@Param("memeId") long memeId);

    /** 按 ID 游标查询已发布梗，只读取 ID 列并限制返回数量。 */
    @Select("SELECT id FROM meme WHERE status = 1 AND id > #{afterId} ORDER BY id ASC LIMIT #{limit}")
    java.util.List<Long> selectPublishedIdsAfterId(@Param("afterId") long afterId,
                                                    @Param("limit") int limit);

    /**
     * 关键字搜索梗（按名称、介绍或标签名模糊匹配），分页，支持排序
     *
     * @param page       分页对象
     * @param keyword    关键字，已做 trim，为空则不匹配任何记录
     * @param mostLikes  最多点赞（有值则按点赞降序）
     * @param mostViews  最多浏览（有值则按浏览降序）
     * @param mostComments 最多评论（有值则按评论降序）
     * @param tagId        可选标签 ID；提供时仅返回关联该标签的梗
     * @return 分页结果
     */
    IPage<Meme> searchByKeyword(Page<Meme> page,
                               @Param("keyword") String keyword,
                               @Param("mostLikes") String mostLikes,
                               @Param("mostViews") String mostViews,
                               @Param("mostComments") String mostComments,
                               @Param("tagId") Long tagId);

    IPage<Meme> selectFollowedTagPage(Page<Meme> page, @Param("userId") Long userId);

    @Update("UPDATE meme SET likes = IFNULL(likes, 0) + 1 WHERE id = #{memeId} AND status = 1")
    int incrementLikes(@Param("memeId") long memeId);

    @Update("UPDATE meme SET likes = GREATEST(IFNULL(likes, 0) - 1, 0) WHERE id = #{memeId}")
    int decrementLikes(@Param("memeId") long memeId);

    /** 在创建评论的数据库事务中同步递增累计评论数。 */
    @Update("UPDATE meme SET comments = IFNULL(comments, 0) + 1 WHERE id = #{memeId}")
    int incrementComments(@Param("memeId") long memeId);

    @Update("UPDATE meme SET page_views = IFNULL(page_views, 0) + 1 WHERE id = #{memeId} AND status = 1")
    int incrementPageViews(@Param("memeId") long memeId);
}
