package com.sakana.just_because_meme_understands_you.mapper;

import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 梗 Mapper
 *
 * @author sakana
 * @since 2026-03-06
 */
@Mapper
public interface MemeMapper extends BaseMapper<Meme> {

    /**
     * 关键字搜索梗（按名称、介绍或标签名模糊匹配），分页，支持排序
     *
     * @param page       分页对象
     * @param keyword    关键字，已做 trim，为空则不匹配任何记录
     * @param mostLikes  最多点赞（有值则按点赞降序）
     * @param mostViews  最多浏览（有值则按浏览降序）
     * @param mostComments 最多评论（有值则按评论降序）
     * @return 分页结果
     */
    IPage<Meme> searchByKeyword(Page<Meme> page,
                               @Param("keyword") String keyword,
                               @Param("mostLikes") String mostLikes,
                               @Param("mostViews") String mostViews,
                               @Param("mostComments") String mostComments);

    @Update("UPDATE meme SET likes = IFNULL(likes, 0) + 1 WHERE id = #{memeId} AND status = 1")
    int incrementLikes(@Param("memeId") long memeId);

    @Update("UPDATE meme SET likes = GREATEST(IFNULL(likes, 0) - 1, 0) WHERE id = #{memeId}")
    int decrementLikes(@Param("memeId") long memeId);

    @Update("UPDATE meme SET page_views = IFNULL(page_views, 0) + 1 WHERE id = #{memeId} AND status = 1")
    int incrementPageViews(@Param("memeId") long memeId);
}
