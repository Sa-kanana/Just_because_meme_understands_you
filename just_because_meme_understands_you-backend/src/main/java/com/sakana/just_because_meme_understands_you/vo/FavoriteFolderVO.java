package com.sakana.just_because_meme_understands_you.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏夹 VO。默认夹 id=0，isDefault=true。
 * coverUrl 动态取夹内首张梗图，无需 DB 存储。
 */
@Data
public class FavoriteFolderVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String description;

    /** 动态封面：夹内首张梗图 URL，无内容时 null */
    private String coverUrl;

    private Integer isPublic;

    private Integer sortOrder;

    private Integer memeCount;

    private Boolean isDefault;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
