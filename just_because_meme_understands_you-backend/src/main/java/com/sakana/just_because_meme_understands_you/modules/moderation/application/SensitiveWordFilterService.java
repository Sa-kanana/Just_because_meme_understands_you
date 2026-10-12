package com.sakana.just_because_meme_understands_you.modules.moderation.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.SensitiveLevel;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.SensitiveScene;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.SensitiveWordRule;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.entity.SensitiveWord;
import com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.filter.SensitiveFilterResult;
import com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.filter.SensitiveWordEngine;
import com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.persistence.mapper.SensitiveWordDictionaryVersionMapper;
import com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.persistence.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 中文注释：从数据库加载词库并通过内存快照为各业务提供统一的敏感词检查。 */
@Slf4j
@Service
public class SensitiveWordFilterService {

    /** 中文注释：多实例词库刷新通知频道。 */
    public static final String RELOAD_CHANNEL = "sensitive_word_clear";
    /** 中文注释：Redis Pub/Sub 丢失消息后的版本兜底检查间隔（毫秒）。 */
    private static final long VERSION_CHECK_INTERVAL_MS = 300_000L;

    /** 中文注释：请求匹配只读取原子快照，不访问数据库或 Redis。 */
    private final SensitiveWordEngine engine = new SensitiveWordEngine();

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    @Resource
    private SensitiveWordDictionaryVersionMapper versionMapper;

    @PostConstruct
    public void init() {
        reload();
    }

    /** 中文注释：读取一致版本的启用规则，构建新 DFA 后原子替换当前快照。 */
    public void reload() {
        for (int attempt = 0; attempt < 5; attempt++) {
            Long versionBefore = versionMapper.selectVersion();
            List<SensitiveWord> words = sensitiveWordMapper.selectList(
                    new LambdaQueryWrapper<SensitiveWord>().eq(SensitiveWord::getStatus, 1));
            Long versionAfter = versionMapper.selectVersion();
            if (versionBefore == null || versionAfter == null) {
                throw new IllegalStateException("敏感词词库版本记录缺失");
            }
            if (!versionBefore.equals(versionAfter)) {
                continue;
            }

            List<SensitiveWordRule> rules = new ArrayList<>();
            if (words != null) {
                for (SensitiveWord word : words) {
                    if (word == null || !StringUtils.hasText(word.getWord())) {
                        continue;
                    }
                    rules.add(new SensitiveWordRule(
                            word.getWord(), word.getCategory(), SensitiveLevel.fromValue(word.getRiskLevel()),
                            word.getReplacement()));
                }
            }
            engine.rebuild(rules, versionAfter);
            log.info("敏感词库已重载, size={}, version={}", rules.size(), versionAfter);
            return;
        }
        throw new IllegalStateException("敏感词词库持续变更，无法构建一致快照");
    }

    /** 中文注释：仅当数据库版本与当前实例不同步时执行一次词库重载。 */
    public void reloadIfVersionChanged() {
        Long version = versionMapper.selectVersion();
        if (version == null) {
            log.warn("敏感词词库版本记录缺失，跳过本次同步");
            return;
        }
        if (version != engine.version()) {
            reload();
        }
    }

    /** 中文注释：暴露当前快照版本供消息监听器跳过过期通知。 */
    public long getLoadedVersion() {
        return engine.version();
    }

    /** 中文注释：定期核对数据库版本，补偿 Redis Pub/Sub 消息在实例离线时丢失的情况。 */
    @Scheduled(fixedDelay = VERSION_CHECK_INTERVAL_MS)
    public void checkDictionaryVersion() {
        try {
            reloadIfVersionChanged();
        } catch (Exception exception) {
            log.warn("定时同步敏感词词库失败，保留当前已加载版本={}", engine.version(), exception);
        }
    }

    /** 中文注释：检查并过滤评论；评论没有待审状态，因此 REVIEW 与 BLOCK 都拒绝写入。 */
    public String filterForInsert(String content) {
        SensitiveFilterResult result = engine.filter(content);
        if (result.getDecision() == SensitiveFilterResult.Decision.BLOCK) {
            throw new BizException(Result.CODE_ERROR, "评论包含违规内容，无法发布");
        }
        if (result.getDecision() == SensitiveFilterResult.Decision.REVIEW) {
            throw new BizException(Result.CODE_ERROR, "评论命中需要人工审核的内容，请修改后再试");
        }
        return result.getContent();
    }

    /** 中文注释：过滤梗审核稿；REVIEW 保留原文并进入调用方已有的梗审核队列。 */
    public String filterForReviewableContent(String content, SensitiveScene scene) {
        requireScene(scene);
        SensitiveFilterResult result = engine.filter(content);
        if (result.getDecision() == SensitiveFilterResult.Decision.BLOCK) {
            throw new BizException(Result.CODE_BAD_REQUEST, sceneBlockMessage(scene));
        }
        if (result.getDecision() == SensitiveFilterResult.Decision.REVIEW && !hasModerationQueue(scene)) {
            throw new BizException(Result.CODE_BAD_REQUEST, sceneReviewMessage(scene));
        }
        return result.getContent();
    }

    /** 中文注释：过滤没有审核队列的即时资料字段；REVIEW 与 BLOCK 均拒绝提交。 */
    public String filterForImmediateContent(String content, SensitiveScene scene) {
        requireScene(scene);
        SensitiveFilterResult result = engine.filter(content);
        if (result.getDecision() == SensitiveFilterResult.Decision.BLOCK) {
            throw new BizException(Result.CODE_BAD_REQUEST, sceneBlockMessage(scene));
        }
        if (result.getDecision() == SensitiveFilterResult.Decision.REVIEW) {
            throw new BizException(Result.CODE_BAD_REQUEST, sceneReviewMessage(scene));
        }
        return result.getContent();
    }

    /** 中文注释：确保场景已明确配置，避免调用方漏传场景而绕过审核策略。 */
    private void requireScene(SensitiveScene scene) {
        if (scene == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "敏感词检查场景不能为空");
        }
    }

    /** 中文注释：判断该业务是否存在可以承接 REVIEW 命中的持久化审核队列。 */
    private boolean hasModerationQueue(SensitiveScene scene) {
        return scene == SensitiveScene.MEME_TITLE || scene == SensitiveScene.MEME_DESCRIPTION
                || scene == SensitiveScene.MEME_RESOURCE_TITLE;
    }

    /** 中文注释：按业务场景生成不泄露具体敏感词的拒绝提示。 */
    private String sceneBlockMessage(SensitiveScene scene) {
        return switch (scene) {
            case MEME_TITLE, MEME_DESCRIPTION, MEME_RESOURCE_TITLE -> "梗内容包含违规词语，无法提交";
            case COMMENT -> "评论包含违规内容，无法发布";
            case USER_NICKNAME -> "昵称包含违规词语，无法保存";
            case USER_SIGNATURE -> "个性签名包含违规词语，无法保存";
        };
    }

    /** 中文注释：没有人工审核队列的场景命中 REVIEW 时使用的提示。 */
    private String sceneReviewMessage(SensitiveScene scene) {
        return switch (scene) {
            case MEME_TITLE, MEME_DESCRIPTION, MEME_RESOURCE_TITLE -> "梗内容需要人工审核，请修改后再试";
            case COMMENT -> "评论命中需要人工审核的内容，请修改后再试";
            case USER_NICKNAME -> "昵称需要人工审核，请修改后再试";
            case USER_SIGNATURE -> "个性签名需要人工审核，请修改后再试";
        };
    }
}
