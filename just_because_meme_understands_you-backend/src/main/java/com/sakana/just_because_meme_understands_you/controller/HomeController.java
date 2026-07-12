package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.service.home.IHomeImageService;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeService;
import com.sakana.just_because_meme_understands_you.vo.HomeImageVO;
import com.sakana.just_because_meme_understands_you.vo.MemeDetailVO;
import com.sakana.just_because_meme_understands_you.vo.MemeListItemVO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.SimpleMemeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@RestController
@RequestMapping
public class HomeController {

    @Resource
    private IMemeService memeService;

    @Resource
    private IHomeImageService homeImageService;

    /**
     * 首页轮播图展示（先按权重排序，后按创建时间排序）
     * GET /image
     */
    @GetMapping("/image")
    public Result<List<HomeImageVO>> carousel() {
        log.info("请求首页轮播图");
        return Result.success(homeImageService.listCarousel());
    }

    /**
     * 梗的分页展示
     * GET /list?page=1&sort=hot&size=16
     * sort: hot（默认）/ new / comments / views / following（需登录）
     */
    @GetMapping("/list")
    public Result<PageVO<MemeListItemVO>> list(
            @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
            @RequestParam(value = "sort", required = false, defaultValue = "hot") String sort,
            @RequestParam(value = "size", required = false, defaultValue = "16") Integer size,
            HttpServletRequest httpServletRequest
    ) {
        log.info("请求梗分页列表, page={}, sort={}, size={}", page, sort, size);
        String normalizedSort = sort != null ? sort.trim().toLowerCase() : "hot";
        Long currentUserId = AuthContext.currentUserId(httpServletRequest);
        if ("following".equals(normalizedSort) && currentUserId == null) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "请先登录后查看关注动态");
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
     * GET /search?keyword=关键字&mostLikes=1&mostViews=1&mostComments=1
     * - keyword: 关键字（必填）
     * - mostLikes: 最多点赞（可选，有值则按点赞降序）
     * - mostViews: 最多浏览（可选，有值则按浏览降序）
     * - mostComments: 最多评论（可选，有值则按评论降序）
     * 响应：code, message, data（SimpleMeme 数组）
     */
    @GetMapping("/search")
    public Result<List<SimpleMemeVO>> search(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "mostLikes", required = false) String mostLikes,
            @RequestParam(value = "mostViews", required = false) String mostViews,
            @RequestParam(value = "mostComments", required = false) String mostComments
    ) {
        log.info("请求关键字搜索, keyword={}, mostLikes={}, mostViews={}, mostComments={}", keyword, mostLikes, mostViews, mostComments);
        List<SimpleMemeVO> data = memeService.searchByKeywordForApi(keyword, mostLikes, mostViews, mostComments);
        return Result.success(data);
    }

    /**
     * 梗的详细页面
     * GET /?memeId=1
     * 请求参数：
     * - memeId: 梗的 id（前端点击卡片时传递）
     * 响应结构严格符合 Apifox 的 DataRespose<Meme>
     */
    @GetMapping("/detail")
    public Result<MemeDetailVO> detail(
            @RequestParam(value = "memeId", required = false) Integer memeId,
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
