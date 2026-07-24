package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

@Data
public class AiIngestBackfillVO {

    /** 扫描到的已发布梗数量 */
    private int scanned;

    /** 成功提交灌库的数量 */
    private int queued;

    /** 跳过（Agent 未配置等） */
    private int skipped;
}
