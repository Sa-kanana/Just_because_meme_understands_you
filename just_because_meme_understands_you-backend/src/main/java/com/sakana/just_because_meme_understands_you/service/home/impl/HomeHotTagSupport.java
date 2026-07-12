package com.sakana.just_because_meme_understands_you.service.home.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.vo.HomeHotTagVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class HomeHotTagSupport {

    @Resource
    private MemeTagMapper memeTagMapper;

    public List<HomeHotTagVO> listHotTags(int limit) {
        List<MemeTag> tags = memeTagMapper.selectList(new LambdaQueryWrapper<MemeTag>()
                .isNotNull(MemeTag::getName)
                .ne(MemeTag::getName, ""));

        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        List<HomeHotTagVO> result = new ArrayList<>();
        for (MemeTag tag : tags) {
            int memeCount = parseRelatedQuantity(tag.getRelatedQuantity());
            if (memeCount <= 0) {
                continue;
            }
            HomeHotTagVO vo = new HomeHotTagVO();
            vo.setId(tag.getId());
            vo.setName(tag.getName());
            vo.setMemeCount(memeCount);
            vo.setHeatScore(memeCount);
            result.add(vo);
        }

        result.sort(Comparator
                .comparing(HomeHotTagVO::getMemeCount, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(HomeHotTagVO::getId, Comparator.nullsLast(Comparator.reverseOrder())));

        if (result.size() <= limit) {
            return result;
        }
        return new ArrayList<>(result.subList(0, limit));
    }

    private static int parseRelatedQuantity(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
