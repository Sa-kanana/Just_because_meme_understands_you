package com.sakana.just_because_meme_understands_you.modules.home.application;

import com.sakana.just_because_meme_understands_you.api.user.vo.HomeBootstrapVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.HomeHotTagsVO;

public interface IHomePageService {

    /**
     * 首页首屏聚合数据。
     */
    HomeBootstrapVO bootstrap(int hotLimit, int tagLimit, String feedSort, int feedSize);

    /**
     * 热门标签列表。
     */
    HomeHotTagsVO listHotTags(int limit);
}
