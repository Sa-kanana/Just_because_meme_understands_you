package com.sakana.just_because_meme_understands_you.api.user.dto;

import lombok.Data;

import java.util.List;

/**
 * 发布梗请求
 *
 * @author sakana
 */
@Data
public class MemeCreateRequestDTO {

    /** 梗名称 */
    private String name;

    /** 梗介绍 */
    private String introduction;

    /** 封面图 URL（选填；前端直传 OSS 后回传） */
    private String image;

    /** 中文注释：代表梗主要分类的标签 ID。 */
    private Integer primaryTagId;

    /** 中文注释：梗关联的普通标签 ID，发布时要求选择 3～7 个。 */
    private List<Integer> tagIds;

    /** 相关资源 URL 列表（图片/视频/GIF，前端直传 OSS 后回传） */
    private List<String> resourceUrls;

    /** 结构化相关链接（标题 + 外链） */
    private List<MemeResourceItemDTO> resources;
}
