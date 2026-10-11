package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 中文注释：文件资产元数据的持久化映射。 */
@Mapper
public interface FileAssetMapper extends BaseMapper<FileAsset> {

    /** 中文注释：在当前事务中按对象键锁定资产，串行化引用替换和删除状态变更。 */
    @Select("SELECT * FROM file_asset WHERE object_key = #{objectKey} FOR UPDATE")
    FileAsset selectByObjectKeyForUpdate(@Param("objectKey") String objectKey);
}
