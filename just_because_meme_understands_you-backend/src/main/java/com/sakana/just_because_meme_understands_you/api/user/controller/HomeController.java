package com.sakana.just_because_meme_understands_you.api.user.controller;

import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.security.AuthContext;
import com.sakana.just_because_meme_understands_you.modules.home.application.IHomeImageService;
import com.sakana.just_because_meme_understands_you.modules.meme.application.IMemeService;
import com.sakana.just_because_meme_understands_you.api.user.vo.HomeImageVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeDetailVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeListItemVO;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.SimpleMemeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/v1")
public class HomeController {

    @Resource
    private IMemeService memeService;

    @Resource
    private IHomeImageService homeImageService;

    /**
     * 首页轮播图展示（先按权重排序，后按创建时间排序）
     * GET /v1/home/images
     */
    @GetMapping("/home/images")
    public Result<List<HomeImageVO>> carousel() {
        log.info("请求首页轮播图");
        return Result.success(homeImageService.listCarousel());
    }

    /**
     * 梗的分页展示
     * GET /v1/memes?page=1&sort=hot&size=16
     * sort: hot（默认）/ new / comments / views / following / tags（后两者需登录）
     */
    @GetMapping("/memes")
    public Result<PageVO<MemeListItemVO>> list(
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "sort", required = false, defaultValue = "hot") String sort,
            @RequestParam(value = "size", required = false, defaultValue = "16") Integer size,
            HttpServletRequest httpServletRequest
    ) {
        log.info("请求梗分页列表, page={}, sort={}, size={}", page, sort, size);
        String normalizedSort = sort != null ? sort.trim().toLowerCase() : "hot";
        Long currentUserId = AuthContext.currentUserId(httpServletRequest);
        if (("following".equals(normalizedSort) || "tags".equals(normalizedSort)) && currentUserId == null) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "请先登录后查看关注动态");
        }
        if ("tags".equals(normalizedSort)) {
            return Result.success(memeService.pageFollowedTagFeed(
                    page != null ? page : 1,
                    size != null ? size : IMemeService.PAGE_SIZE,
                    currentUserId));
        }
        PageVO<MemeListItemVO> data = memeService.pageMemeFeed(
                page != null ? page : 1,
                size != null ? size : IMemeService.PAGE_SIZE,
                normalizedSort,
                currentUserId
        );
        return Result.success(data);
    }

    /**
     * 关键字搜索（接口定义）
     * GET /v1/meme-searches?keyword=关键字&mostLikes=1&mostViews=1&mostComments=1
     * - keyword: 关键字（必填）
     * - mostLikes: 最多点赞（可选，有值则按点赞降序）
     * - mostViews: 最多浏览（可选，有值则按浏览降序）
     * - mostComments: 最多评论（可选，有值则按评论降序）
     * 响应：code, message, data（SimpleMeme 数组）
     */
    @GetMapping("/meme-searches")
    public Result<List<SimpleMemeVO>> search(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "mostLikes", required = false) String mostLikes,
            @RequestParam(value = "mostViews", required = false) String mostViews,
            @RequestParam(value = "mostComments", required = false) String mostComments,
            @RequestParam(value = "tagId", required = false) Long tagId
    ) {
        log.info("请求梗搜索, keyword={}, tagId={}, mostLikes={}, mostViews={}, mostComments={}",
                keyword, tagId, mostLikes, mostViews, mostComments);
        List<SimpleMemeVO> data = memeService.searchByKeywordForApi(keyword, mostLikes, mostViews, mostComments, tagId);
        return Result.success(data);
    }

    /**
     * 梗的详细页面
     * GET /v1/memes/{memeId}
     * 请求参数：
     * - memeId: 梗的 id（前端点击卡片时传递）
     * 响应结构严格符合 Apifox 的 DataRespose<Meme>
     */
    @GetMapping("/memes/{memeId}")
    public Result<MemeDetailVO> detail(
            @PathVariable("memeId") Long memeId,
            HttpServletRequest httpServletRequest
    ) {
        log.info("请求梗的详细页面, memeId={}", memeId);
        if (memeId == null) {
            return Result.fail(Result.CODE_BAD_REQUEST, "memeId 不能为空");
        }
        Long currentUserId = AuthContext.currentUserId(httpServletRequest);
        MemeDetailVO detail = memeService.getMemeDetail(memeId, currentUserId);
        if (detail == null) {
            return Result.fail(Result.CODE_NOT_FOUND, "梗不存在");
        }
        return Result.success(detail);
    }
}
