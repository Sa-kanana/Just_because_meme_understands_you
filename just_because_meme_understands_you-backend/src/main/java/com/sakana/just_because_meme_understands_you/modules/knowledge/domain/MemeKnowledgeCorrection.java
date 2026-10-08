package com.sakana.just_because_meme_understands_you.modules.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("meme_knowledge_correction")
public class MemeKnowledgeCorrection {
    @TableId(type = IdType.AUTO) private Long id;
    private Long memeId;
    private Long userId;
    private String changesJson;
    private String status;
    private Long handledBy;
    private String handlingNote;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
