package com.sakana.just_because_meme_understands_you.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemePageViewVO {

    private Long memeId;

    private Integer pageViews;

    /** 本次上报是否计入浏览量；查询接口不返回该字段 */
    private Boolean counted;
}
