package com.sakana.just_because_meme_understands_you.modules.user.application.like.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeLikeVO;
import com.sakana.just_because_meme_understands_you.common.mapping.MapStructConfig;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserLike;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 用户点赞记录到 API 视图的映射器。 */
@Mapper(config = MapStructConfig.class)
public interface UserLikeViewMapper {

    /** 映射点赞记录，梗的聚合点赞数由应用服务计算。 */
    @Mapping(target = "likeId", source = "source.id")
    @Mapping(target = "memeId", source = "source.memeId")
    @Mapping(target = "liked", source = "liked")
    @Mapping(target = "likeCount", ignore = true)
    @Mapping(target = "createTime", source = "source.createTime")
    MemeLikeVO toMemeLikeVO(UserLike source, boolean liked);
}
