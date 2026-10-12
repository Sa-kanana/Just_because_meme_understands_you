package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 中文注释：以主键游标分页读取历史业务表中的文件值，不改写原有业务字段。 */
@Mapper
public interface FileAssetLegacyReferenceMapper {

    /** 中文注释：逐项读取 user.avatar，并以 user.id 同时作为游标和业务引用ID。 */
    @Select("SELECT id AS sourceId, id AS bizId, avatar AS objectValue FROM `user` "
            + "WHERE id > COALESCE(#{lastSourceId}, 0) AND avatar IS NOT NULL AND TRIM(avatar) <> '' "
            + "ORDER BY id LIMIT #{limit} FOR UPDATE")
    List<LegacyFileAssetReferenceRow> selectUserAvatarBatch(
            @Param("lastSourceId") Long lastSourceId, @Param("limit") int limit);

    /** 中文注释：逐项读取 meme.image，并以 meme.id 作为封面引用的业务ID。 */
    @Select("SELECT id AS sourceId, id AS bizId, image AS objectValue FROM meme "
            + "WHERE id > COALESCE(#{lastSourceId}, 0) AND image IS NOT NULL AND TRIM(image) <> '' "
            + "ORDER BY id LIMIT #{limit} FOR UPDATE")
    List<LegacyFileAssetReferenceRow> selectMemeCoverBatch(
            @Param("lastSourceId") Long lastSourceId, @Param("limit") int limit);

    /** 中文注释：逐项读取 meme_resource.resource_url，并引用实际的资源行ID。 */
    @Select("SELECT id AS sourceId, id AS bizId, resource_url AS objectValue FROM meme_resource "
            + "WHERE id > COALESCE(#{lastSourceId}, 0) AND resource_url IS NOT NULL AND TRIM(resource_url) <> '' "
            + "ORDER BY id LIMIT #{limit} FOR UPDATE")
    List<LegacyFileAssetReferenceRow> selectMemeResourceBatch(
            @Param("lastSourceId") Long lastSourceId, @Param("limit") int limit);

    /** 中文注释：逐项读取 meme_comment_images.url，并引用实际的评论图片行ID。 */
    @Select("SELECT id AS sourceId, id AS bizId, url AS objectValue FROM meme_comment_images "
            + "WHERE id > COALESCE(#{lastSourceId}, 0) AND url IS NOT NULL AND TRIM(url) <> '' "
            + "ORDER BY id LIMIT #{limit} FOR UPDATE")
    List<LegacyFileAssetReferenceRow> selectCommentImageBatch(
            @Param("lastSourceId") Long lastSourceId, @Param("limit") int limit);

    /** 中文注释：逐项读取 home_image.img_url，并以首页图片行ID维护后台图像引用。 */
    @Select("SELECT id AS sourceId, id AS bizId, img_url AS objectValue FROM home_image "
            + "WHERE id > COALESCE(#{lastSourceId}, 0) AND img_url IS NOT NULL AND TRIM(img_url) <> '' "
            + "ORDER BY id LIMIT #{limit} FOR UPDATE")
    List<LegacyFileAssetReferenceRow> selectBannerImageBatch(
            @Param("lastSourceId") Long lastSourceId, @Param("limit") int limit);

    /** 中文注释：仅读取仍待审核的 meme_edit_revision.content_json，避免已迁移审核稿重复保留文件。 */
    @Select("SELECT id AS sourceId, id AS bizId, content_json AS contentJson FROM meme_edit_revision "
            + "WHERE id > COALESCE(#{lastSourceId}, 0) AND status = 'PENDING' "
            + "ORDER BY id LIMIT #{limit} FOR UPDATE")
    List<LegacyFileAssetReferenceRow> selectMemeRevisionBatch(
            @Param("lastSourceId") Long lastSourceId, @Param("limit") int limit);
}
