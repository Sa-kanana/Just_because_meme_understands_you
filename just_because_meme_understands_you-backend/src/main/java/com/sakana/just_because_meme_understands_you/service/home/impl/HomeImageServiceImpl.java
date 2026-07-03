package com.sakana.just_because_meme_understands_you.service.home.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.entity.HomeImage;
import com.sakana.just_because_meme_understands_you.mapper.HomeImageMapper;
import com.sakana.just_because_meme_understands_you.service.home.IHomeImageService;
import com.sakana.just_because_meme_understands_you.vo.HomeImageVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 首页轮播图服务实现：先按权重排序，再按创建时间排序；使用 Redis 缓存
 */
@Slf4j
@Service
public class HomeImageServiceImpl extends ServiceImpl<HomeImageMapper, HomeImage> implements IHomeImageService {

    private static final String CACHE_KEY = "home:carousel:images";
    private static final long CACHE_MINUTES = 5;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @Override
    public List<HomeImageVO> listCarousel() {
        try {
            String json = stringRedisTemplate.opsForValue().get(CACHE_KEY);
            if (json != null && !json.isEmpty()) {
                List<HomeImageVO> cached = objectMapper.readValue(json, new TypeReference<>() {});
                return cached != null ? cached : Collections.emptyList();
            }
        } catch (Exception e) {
            log.warn("读取轮播图缓存失败, key={}", CACHE_KEY, e);
        }

        LambdaQueryWrapper<HomeImage> wrapper = new LambdaQueryWrapper<HomeImage>()
                .eq(HomeImage::getStatus, 1)
                .orderByDesc(HomeImage::getSortOrder)
                .orderByDesc(HomeImage::getCreateTime);
        List<HomeImage> list = list(wrapper);
        if (CollectionUtils.isEmpty(list)) {
            return Collections.emptyList();
        }

        List<HomeImageVO> voList = list.stream().map(this::toVO).collect(Collectors.toList());
        try {
            String jsonValue = objectMapper.writeValueAsString(voList);
            stringRedisTemplate.opsForValue().set(CACHE_KEY, jsonValue != null ? jsonValue : "[]", CACHE_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入轮播图缓存失败, key={}", CACHE_KEY, e);
        }
        return voList;
    }

    private HomeImageVO toVO(HomeImage e) {
        HomeImageVO vo = new HomeImageVO();
        vo.setTitle(e.getTitle());
        vo.setImgUrl(e.getImgUrl());
        vo.setTargetType(e.getTargetType());
        vo.setTargetValue(e.getTargetValue());
        vo.setSortOrder(e.getSortOrder());
        vo.setStatus(e.getStatus());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }
}
