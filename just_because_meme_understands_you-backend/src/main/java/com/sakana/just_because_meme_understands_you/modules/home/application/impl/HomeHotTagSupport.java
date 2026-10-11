package com.sakana.just_because_meme_understands_you.modules.home.application.impl;

import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.api.user.vo.HomeHotTagVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class HomeHotTagSupport {

    @Resource
    private MemeCatalogPort memeCatalogPort;

    public List<HomeHotTagVO> listHotTags(int limit) {
        List<MemeCatalogPort.MemeTagSummary> tags = memeCatalogPort.findHotTags(limit);

        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        List<HomeHotTagVO> result = new ArrayList<>();
        for (MemeCatalogPort.MemeTagSummary tag : tags) {
            int memeCount = tag.relatedQuantity();
            HomeHotTagVO vo = new HomeHotTagVO();
            vo.setId(tag.id().intValue());
            vo.setName(tag.name());
            vo.setMemeCount(memeCount);
            vo.setHeatScore(memeCount);
            result.add(vo);
        }

        return result;
    }
}
