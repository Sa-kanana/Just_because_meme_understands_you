package com.sakana.just_because_meme_understands_you.modules.home.application.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.HomeImageVO;
import com.sakana.just_because_meme_understands_you.common.mapping.MapStructConfig;
import com.sakana.just_because_meme_understands_you.modules.home.domain.entity.HomeImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 首页图片视图的基础字段映射器。
 */
@Mapper(config = MapStructConfig.class)
public interface HomeImageViewMapper {

    /**
     * 将首页图片实体转换为基础视图，公开 URL 由应用层转换。
     *
     * @param source 首页图片实体
     * @return 首页图片视图
     */
    @Mapping(target = "imgUrl", ignore = true)
    HomeImageVO toHomeImageVO(HomeImage source);
}
