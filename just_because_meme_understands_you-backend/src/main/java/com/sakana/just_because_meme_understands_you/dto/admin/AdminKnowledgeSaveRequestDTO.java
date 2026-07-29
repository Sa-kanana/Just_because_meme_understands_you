package com.sakana.just_because_meme_understands_you.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AdminKnowledgeSaveRequestDTO {

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最多 200 字")
    private String title;

    @NotBlank(message = "正文不能为空")
    @Size(max = 200000, message = "正文过长")
    private String content;

    @Size(max = 64, message = "分类最多 64 字")
    private String category;

    private List<String> tags = new ArrayList<>();
}
