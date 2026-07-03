package com.sakana.just_because_meme_understands_you.service.comment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.entity.SensitiveWord;
import com.sakana.just_because_meme_understands_you.filter.SensitiveFilterResult;
import com.sakana.just_because_meme_understands_you.filter.SensitiveWordEngine;
import com.sakana.just_because_meme_understands_you.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SensitiveWordFilterService {

    public static final String RELOAD_CHANNEL = "sensitive_word_clear";

    private final SensitiveWordEngine engine = new SensitiveWordEngine();

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized void reload() {
        List<SensitiveWord> words = sensitiveWordMapper.selectList(
                new LambdaQueryWrapper<SensitiveWord>()
                        .eq(SensitiveWord::getStatus, 1)
        );
        Map<String, Integer> wordActionMap = new LinkedHashMap<>();
        if (words != null) {
            for (SensitiveWord word : words) {
                if (word == null || word.getWord() == null || word.getWord().isBlank()) {
                    continue;
                }
                wordActionMap.put(word.getWord().trim(), word.getActionType() == null ? 1 : word.getActionType());
            }
        }
        engine.rebuild(wordActionMap);
        log.info("敏感词库已重载, size={}", wordActionMap.size());
    }

    public String filterForInsert(String content) {
        SensitiveFilterResult result = engine.filter(content);
        if (result.isRejected()) {
            throw new BizException(Result.CODE_ERROR, "评论包含违规内容，无法发布");
        }
        if (result.isNeedAudit()) {
            throw new BizException(Result.CODE_ERROR, "评论需要人工审核，请修改后再试");
        }
        return result.getContent();
    }
}
