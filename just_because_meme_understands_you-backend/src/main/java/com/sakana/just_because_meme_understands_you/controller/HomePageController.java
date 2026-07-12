package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.service.home.IHomePageService;
import com.sakana.just_because_meme_understands_you.vo.HomeBootstrapVO;
import com.sakana.just_because_meme_understands_you.vo.HomeHotTagsVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/home")
public class HomePageController {

    @Resource
    private IHomePageService homePageService;

    /**
     * 首页首屏聚合接口。
     * GET /home/bootstrap?hotLimit=6&tagLimit=10&feedSort=hot&feedSize=16
     */
    @GetMapping("/bootstrap")
    public Result<HomeBootstrapVO> bootstrap(
            @RequestParam(value = "hotLimit", required = false, defaultValue = "6") Integer hotLimit,
            @RequestParam(value = "tagLimit", required = false, defaultValue = "10") Integer tagLimit,
            @RequestParam(value = "feedSort", required = false, defaultValue = "hot") String feedSort,
            @RequestParam(value = "feedSize", required = false, defaultValue = "16") Integer feedSize
    ) {
        log.info("请求首页 bootstrap, hotLimit={}, tagLimit={}, feedSort={}, feedSize={}",
                hotLimit, tagLimit, feedSort, feedSize);
        HomeBootstrapVO data = homePageService.bootstrap(
                hotLimit != null ? hotLimit : 6,
                tagLimit != null ? tagLimit : 10,
                feedSort,
                feedSize != null ? feedSize : 16
        );
        return Result.success(data);
    }

    /**
     * 热门标签。
     * GET /home/hot-tags?limit=10
     */
    @GetMapping("/hot-tags")
    public Result<HomeHotTagsVO> hotTags(
            @RequestParam(value = "limit", required = false, defaultValue = "10") Integer limit
    ) {
        log.info("请求热门标签, limit={}", limit);
        return Result.success(homePageService.listHotTags(limit != null ? limit : 10));
    }
}
