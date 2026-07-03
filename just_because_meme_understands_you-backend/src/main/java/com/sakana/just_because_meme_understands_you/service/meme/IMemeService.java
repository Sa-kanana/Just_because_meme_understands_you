package com.sakana.just_because_meme_understands_you.service.meme;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.vo.MemeDetailVO;
import com.sakana.just_because_meme_understands_you.vo.MemeListItemVO;
import com.sakana.just_because_meme_understands_you.vo.SimpleMemeVO;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author sakana
 * @since 2026-03-06
 */
public interface IMemeService extends IService<Meme> {

    /** 每页固定条数 */
    int PAGE_SIZE = 16;

    /**
     * 梗的分页展示，每页固定返回 8 条
     *
     * @param page 页码，从 1 开始，由前端传递
     * @return 当前页的梗列表（包含标签），最多 8 条
     */
    List<MemeListItemVO> pageMemeList(int page);

    /**
     * 关键字搜索梗，按名称、介绍或标签名模糊匹配，分页返回（每页 8 条）
     *
     * @param keyword 关键字，为空或空白时返回空列表
     * @param page    页码，从 1 开始
     * @return 当前页的梗列表（包含标签）
     */
    List<MemeListItemVO> searchByKeyword(String keyword, int page);

    /**
     * 关键字搜索（接口 /search）：按关键字搜索，支持 mostLikes/mostViews/mostComments 排序
     *
     * @param keyword      关键字，必填
     * @param mostLikes    最多点赞（有值则按点赞降序）
     * @param mostViews    最多浏览（有值则按浏览降序）
     * @param mostComments 最多评论（有值则按评论降序）
     * @return SimpleMeme 列表，符合接口 DataResponse.data
     */
    List<SimpleMemeVO> searchByKeywordForApi(String keyword, String mostLikes, String mostViews, String mostComments);

    /**
     * 梗的详细页面：根据 memeId 查询梗的详细信息（包含标签和相关链接）
     *
     * @param memeId 梗的 id
     * @return 梗的详细信息，找不到返回 null
     */
    MemeDetailVO getMemeDetail(Integer memeId);
}

