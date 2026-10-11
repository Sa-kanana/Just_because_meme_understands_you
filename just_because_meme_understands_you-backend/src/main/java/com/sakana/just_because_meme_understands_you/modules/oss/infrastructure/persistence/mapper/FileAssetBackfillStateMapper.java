package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetBackfillState;
import org.apache.ibatis.annotations.Mapper;

/** 中文注释：历史文件引用回填游标和租约的持久化映射。 */
@Mapper
public interface FileAssetBackfillStateMapper extends BaseMapper<FileAssetBackfillState> {
}
