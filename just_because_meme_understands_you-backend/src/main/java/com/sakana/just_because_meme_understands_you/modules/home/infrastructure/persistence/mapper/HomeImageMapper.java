package com.sakana.just_because_meme_understands_you.modules.home.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.home.domain.entity.HomeImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 首页轮播图 Mapper
 */
@Mapper
public interface HomeImageMapper extends BaseMapper<HomeImage> {
}
