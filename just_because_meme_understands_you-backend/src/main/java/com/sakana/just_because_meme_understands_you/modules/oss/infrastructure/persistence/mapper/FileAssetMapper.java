package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.domain.entity.FileAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/** 中文注释：文件资产元数据的持久化映射。 */
@Mapper
public interface FileAssetMapper extends BaseMapper<FileAsset> {

    /** 中文注释：返回指定生命周期状态的资产数，供每日对账汇总。 */
    @Select("SELECT COUNT(*) FROM file_asset WHERE status = #{status}")
    long countByStatus(@Param("status") String status);

    /** 中文注释：按唯一对象键幂等登记历史对象；仅将临时或待删资产恢复为活动状态。 */
    @Insert("INSERT INTO file_asset (object_key, owner_id, scene, status, activated_at, created_at, updated_at) "
            + "VALUES (#{objectKey}, NULL, #{scene}, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) "
            + "ON DUPLICATE KEY UPDATE delete_after = IF(status = 'PENDING_DELETE', NULL, delete_after), "
            + "activated_at = IF(status IN ('TEMP', 'PENDING_DELETE'), COALESCE(activated_at, CURRENT_TIMESTAMP), activated_at), "
            + "status = IF(status IN ('TEMP', 'PENDING_DELETE'), 'ACTIVE', status), "
            + "updated_at = CURRENT_TIMESTAMP")
    int insertLegacyAssetIfAbsent(@org.apache.ibatis.annotations.Param("objectKey") String objectKey,
                                  @org.apache.ibatis.annotations.Param("scene") String scene);

    /** 中文注释：按主键锁定资产，保证状态变更与引用增删串行化。 */
    @Select("SELECT * FROM file_asset WHERE id = #{assetId} FOR UPDATE")
    FileAsset selectByIdForUpdate(@Param("assetId") Long assetId);

    /** 中文注释：按对象键查询资产ID，后续按主键顺序加锁以减少死锁风险。 */
    @Select("SELECT * FROM file_asset WHERE object_key = #{objectKey}")
    FileAsset selectByObjectKey(@Param("objectKey") String objectKey);

    /** 中文注释：在当前事务中按对象键锁定资产，串行化引用替换和删除状态变更。 */
    @Select("SELECT * FROM file_asset WHERE object_key = #{objectKey} FOR UPDATE")
    FileAsset selectByObjectKeyForUpdate(@Param("objectKey") String objectKey);

    /** 中文注释：按主键游标读取过期TEMP资产，供每日清理任务分批确认对象是否仍在OSS。 */
    @Select("SELECT * FROM file_asset WHERE status = 'TEMP' AND created_at <= #{cutoff} "
            + "AND id > COALESCE(#{afterId}, 0) ORDER BY id LIMIT #{limit}")
    List<FileAsset> selectExpiredTemporaryAssets(@Param("cutoff") LocalDateTime cutoff,
                                                @Param("afterId") Long afterId,
                                                @Param("limit") int limit);
}
