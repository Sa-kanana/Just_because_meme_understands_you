package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

import java.util.List;

@Data
public class HomeBootstrapVO {

    private List<HomeQuickActionVO> quickActions;

    private List<HomeHotTagVO> hotTags;

    private List<MemeListItemVO> hotMemes;

    private HomeFeedVO feed;
}
