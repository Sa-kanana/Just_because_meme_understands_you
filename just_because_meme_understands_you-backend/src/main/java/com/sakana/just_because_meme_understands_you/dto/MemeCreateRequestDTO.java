package com.sakana.just_because_meme_understands_you.dto;

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

    /** 封面图 URL（前端直传 OSS 后回传） */
    private String image;

    /** 关联标签 id 列表 */
    private List<Integer> tagIds;

    /** 相关资源 URL 列表（图片/视频/GIF，前端直传 OSS 后回传） */
    private List<String> resourceUrls;
}
