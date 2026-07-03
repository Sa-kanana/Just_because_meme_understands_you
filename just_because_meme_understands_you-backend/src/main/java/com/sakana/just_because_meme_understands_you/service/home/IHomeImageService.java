package com.sakana.just_because_meme_understands_you.service.home;

import com.sakana.just_because_meme_understands_you.vo.HomeImageVO;

import java.util.List;

/**
 * 首页轮播图服务
 */
public interface IHomeImageService {

    /**
     * 查询上线轮播图，按权重降序、创建时间降序；优先读 Redis 缓存
     */
    List<HomeImageVO> listCarousel();
}
