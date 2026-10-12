package com.sakana.just_because_meme_understands_you.modules.meme.application.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeDetailVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.SimpleMemeVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.common.mapping.MapStructConfig;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 梗模块 API 视图映射器。 */
@Mapper(config = MapStructConfig.class)
public interface MemeViewMapper {

    /** 将梗实体的基础字段映射到关键字搜索视图，作者、标签由应用层组装。 */
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "memeTag", ignore = true)
    SimpleMemeVO toSimpleMemeVOBase(Meme source);

    /** 将梗实体的基础字段映射到详情视图，动态统计与访问状态由应用层组装。 */
    @Mapping(target = "statusDesc", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "owner", ignore = true)
    @Mapping(target = "ownerPreview", ignore = true)
    @Mapping(target = "commentsEnabled", ignore = true)
    @Mapping(target = "viewMode", ignore = true)
    @Mapping(target = "favoriteEnabled", ignore = true)
    @Mapping(target = "memeTag", ignore = true)
    @Mapping(target = "links", ignore = true)
    MemeDetailVO toMemeDetailVOBase(Meme source);

    /** 将梗标签实体映射为用户端标签视图，关联数由应用层按场景计算。 */
    @Mapping(target = "relatedQuantity", ignore = true)
    @Mapping(target = "isPrimary", source = "isPrimary")
    MemeTagVO toMemeTagVO(MemeTag source);
}
