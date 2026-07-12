package com.sakana.just_because_meme_understands_you.service.home.impl;

import com.sakana.just_because_meme_understands_you.service.home.IHomePageService;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeService;
import com.sakana.just_because_meme_understands_you.vo.HomeBootstrapVO;
import com.sakana.just_because_meme_understands_you.vo.HomeFeedVO;
import com.sakana.just_because_meme_understands_you.vo.HomeHotTagVO;
import com.sakana.just_because_meme_understands_you.vo.HomeHotTagsVO;
import com.sakana.just_because_meme_understands_you.vo.HomeQuickActionVO;
import com.sakana.just_because_meme_understands_you.vo.MemeListItemVO;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class HomePageServiceImpl implements IHomePageService {

    private static final int DEFAULT_HOT_LIMIT = 6;
    private static final int MAX_HOT_LIMIT = 12;
    private static final int DEFAULT_TAG_LIMIT = 10;
    private static final int MAX_TAG_LIMIT = 30;
    private static final int DEFAULT_FEED_SIZE = 16;
    private static final int MAX_FEED_SIZE = 32;
    private static final String DEFAULT_FEED_SORT = "hot";

    @Resource
    private IMemeService memeService;

    @Resource
    private HomeHotTagSupport homeHotTagSupport;

    @Override
    public HomeBootstrapVO bootstrap(int hotLimit, int tagLimit, String feedSort, int feedSize) {
        int safeHotLimit = clamp(hotLimit, DEFAULT_HOT_LIMIT, MAX_HOT_LIMIT);
        int safeTagLimit = clamp(tagLimit, DEFAULT_TAG_LIMIT, MAX_TAG_LIMIT);
        int safeFeedSize = clamp(feedSize, DEFAULT_FEED_SIZE, MAX_FEED_SIZE);
        String safeSort = normalizeFeedSort(feedSort);

        HomeBootstrapVO vo = new HomeBootstrapVO();
        vo.setQuickActions(buildQuickActions());
        vo.setHotTags(homeHotTagSupport.listHotTags(safeTagLimit));
        vo.setHotMemes(memeService.listHotMemes(safeHotLimit));

        PageVO<MemeListItemVO> feedPage = memeService.pageMemeFeed(1, safeFeedSize, safeSort, null);
        vo.setFeed(toHomeFeedVO(feedPage, safeSort));
        return vo;
    }

    @Override
    public HomeHotTagsVO listHotTags(int limit) {
        int safeLimit = clamp(limit, DEFAULT_TAG_LIMIT, MAX_TAG_LIMIT);
        HomeHotTagsVO vo = new HomeHotTagsVO();
        vo.setTags(homeHotTagSupport.listHotTags(safeLimit));
        return vo;
    }

    private static List<HomeQuickActionVO> buildQuickActions() {
        List<HomeQuickActionVO> actions = new ArrayList<>(3);
        actions.add(quickAction("publish", "发布梗", "/publish", true));
        actions.add(quickAction("search", "搜梗", "/search", false));
        actions.add(quickAction("favorites", "我的收藏", "/user/me?tab=favorite", true));
        return actions;
    }

    private static HomeQuickActionVO quickAction(String key, String label, String route, boolean requireLogin) {
        HomeQuickActionVO vo = new HomeQuickActionVO();
        vo.setKey(key);
        vo.setLabel(label);
        vo.setRoute(route);
        vo.setRequireLogin(requireLogin);
        return vo;
    }

    private static HomeFeedVO toHomeFeedVO(PageVO<MemeListItemVO> page, String sort) {
        HomeFeedVO feed = new HomeFeedVO();
        feed.setSort(sort);
        feed.setList(page.getList() != null ? page.getList() : List.of());
        feed.setPage(page.getPage());
        feed.setSize(page.getSize());
        feed.setHasMore(page.getHasMore());
        return feed;
    }

    static String normalizeFeedSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return DEFAULT_FEED_SORT;
        }
        String normalized = sort.trim().toLowerCase();
        return switch (normalized) {
            case "hot", "new", "comments", "views" -> normalized;
            default -> DEFAULT_FEED_SORT;
        };
    }

    private static int clamp(int value, int defaultValue, int max) {
        if (value <= 0) {
            return defaultValue;
        }
        return Math.min(value, max);
    }
}
