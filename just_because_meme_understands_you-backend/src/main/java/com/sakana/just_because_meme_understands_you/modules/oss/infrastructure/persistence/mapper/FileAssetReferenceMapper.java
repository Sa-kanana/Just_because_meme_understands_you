package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAssetReference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 中文注释：文件资产业务引用的持久化映射。 */
@Mapper
public interface FileAssetReferenceMapper extends BaseMapper<FileAssetReference> {

    /** 中文注释：以业务字段唯一键幂等创建历史引用，保留同对象的其他业务引用。 */
    @Insert("INSERT IGNORE INTO file_asset_ref (asset_id, biz_type, biz_id, field_key, created_at) "
            + "VALUES (#{reference.assetId}, #{reference.bizType}, #{reference.bizId}, "
            + "#{reference.fieldKey}, CURRENT_TIMESTAMP)")
    int insertIgnoreLegacyReference(@org.apache.ibatis.annotations.Param("reference") FileAssetReference reference);

    /** 中文注释：统计资产当前仍被多少个业务字段引用。 */
    @Select("SELECT COUNT(*) FROM file_asset_ref WHERE asset_id = #{assetId}")
    long countByAssetId(@Param("assetId") Long assetId);

    /** 中文注释：查询一个业务字段当前绑定的全部引用，兼容迁移期间的重复历史行。 */
    @Select("SELECT * FROM file_asset_ref WHERE biz_type = #{bizType} AND biz_id = #{bizId} AND field_key = #{fieldKey}")
    List<FileAssetReference> selectByBusinessField(
            @Param("bizType") String bizType,
            @Param("bizId") Long bizId,
            @Param("fieldKey") String fieldKey);

    /** 中文注释：锁定目标业务字段已有引用，避免并发替换时丢失更新。 */
    @Select("SELECT * FROM file_asset_ref WHERE biz_type = #{bizType} AND biz_id = #{bizId} "
            + "AND field_key = #{fieldKey} FOR UPDATE")
    List<FileAssetReference> selectByBusinessFieldForUpdate(
            @Param("bizType") String bizType,
            @Param("bizId") Long bizId,
            @Param("fieldKey") String fieldKey);
}
