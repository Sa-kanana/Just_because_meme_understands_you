package com.sakana.just_because_meme_understands_you.modules.meme.application.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.common.mapping.MapStructConfig;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 梗模块 API 视图映射器。 */
@Mapper(config = MapStructConfig.class)
public interface MemeViewMapper {

    /** 将梗标签实体映射为用户端标签视图，关联数由应用层按场景计算。 */
    @Mapping(target = "relatedQuantity", ignore = true)
    MemeTagVO toMemeTagVO(MemeTag source);
}
