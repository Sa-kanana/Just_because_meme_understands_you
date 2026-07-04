package com.sakana.just_because_meme_understands_you.dto;

import lombok.Data;

import java.util.List;

/**
 * 发布梗请求 DTO（两阶段上传：图片 URL 由前端直传 OSS 后回传）
 *
 * @author sakana
 */
@Data
public class MemePublishRequestDTO {

    /** 梗名称 */
    private String name;

    /** 梗介绍 */
    private String introduction;

    /** 封面图 URL（前端已直传 OSS） */
    private String image;

    /** 标签 id 列表 */
    private List<Integer> tagIds;

    /** 相关链接 URL 列表（前端已直传 OSS 或外链） */
    private List<String> resourceUrls;
}
