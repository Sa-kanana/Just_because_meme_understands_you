package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

@Data
public class MemeCrawlResultVO {

    private int fetched;

    private int created;

    private int skipped;

    private int failed;
}
