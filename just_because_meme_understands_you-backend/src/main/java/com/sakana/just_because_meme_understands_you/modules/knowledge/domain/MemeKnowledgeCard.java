package com.sakana.just_because_meme_understands_you.modules.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("meme_knowledge_card")
public class MemeKnowledgeCard {
    @TableId private Long memeId;
    private String sourceUrl;
    private LocalDate firstSeenDate;
    private String popularityContext;
    private String usageExamples;
    private String evolution;
    private String relatedMemeIds;
    private LocalDateTime updatedAt;
}
