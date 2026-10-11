package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 中文注释：文件资产元数据的持久化映射。 */
@Mapper
public interface FileAssetMapper extends BaseMapper<FileAsset> {

    /** 中文注释：按主键锁定资产，保证状态变更与引用增删串行化。 */
    @Select("SELECT * FROM file_asset WHERE id = #{assetId} FOR UPDATE")
    FileAsset selectByIdForUpdate(@Param("assetId") Long assetId);

    /** 中文注释：按对象键查询资产ID，后续按主键顺序加锁以减少死锁风险。 */
    @Select("SELECT * FROM file_asset WHERE object_key = #{objectKey}")
    FileAsset selectByObjectKey(@Param("objectKey") String objectKey);

    /** 中文注释：在当前事务中按对象键锁定资产，串行化引用替换和删除状态变更。 */
    @Select("SELECT * FROM file_asset WHERE object_key = #{objectKey} FOR UPDATE")
    FileAsset selectByObjectKeyForUpdate(@Param("objectKey") String objectKey);
}
