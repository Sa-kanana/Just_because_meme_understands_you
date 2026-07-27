package com.sakana.just_because_meme_understands_you.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 帮助中心文档内容。
 */
@Data
@Builder
public class HelpDocVO {

    /** 文档标题 */
    private String title;

    /** Markdown 正文 */
    private String content;

    /** 内容版本号（便于前端缓存比对） */
    private String version;
}
