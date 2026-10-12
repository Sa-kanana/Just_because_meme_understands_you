package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.pagination.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminSensitiveWordSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.entity.SensitiveWord;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.persistence.mapper.SensitiveWordMapper;
import com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.persistence.mapper.SensitiveWordDictionaryVersionMapper;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserMapper;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminSensitiveWordService;
import com.sakana.just_because_meme_understands_you.modules.admin.application.assembler.AdminViewMapper;
import com.sakana.just_because_meme_understands_you.modules.moderation.application.SensitiveWordFilterService;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import lombok.extern.slf4j.Slf4j;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminSensitiveWordVO;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminSensitiveWordServiceImpl implements IAdminSensitiveWordService {

    private static final Set<String> LEVELS = Set.of("NORMAL", "REVIEW", "BLOCK");
    private static final Set<String> CATEGORIES = Set.of("POLITICS", "PORN", "ABUSE", "AD", "CUSTOM");

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    @Resource
    private AdminViewMapper adminViewMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SensitiveWordDictionaryVersionMapper versionMapper;

    @Override
    public PageVO<AdminSensitiveWordVO> page(Integer page, Integer size, String keyword, String category,
                                              String level, Integer status) {
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SensitiveWord::getWord, keyword.trim());
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(SensitiveWord::getCategory, category.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(level)) {
            wrapper.eq(SensitiveWord::getRiskLevel, level.trim().toUpperCase(Locale.ROOT));
        }
        if (status != null) {
            wrapper.eq(SensitiveWord::getStatus, status);
        }
        wrapper.orderByDesc(SensitiveWord::getId);

        Page<SensitiveWord> mpPage = sensitiveWordMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<SensitiveWord> records = mpPage.getRecords() != null ? mpPage.getRecords() : Collections.emptyList();

        PageVO<AdminSensitiveWordVO> vo = new PageVO<>();
        vo.setList(records.stream().map(this::toVO).collect(Collectors.toList()));
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setTotal(mpPage.getTotal());
        vo.setHasMore(mpPage.getCurrent() * mpPage.getSize() < mpPage.getTotal());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminSensitiveWordVO create(Long adminUserId, AdminSensitiveWordSaveRequestDTO request) {
        validateSave(request);
        String word = request.getWord().trim();
        assertWordUnique(word, null);

        String creator = String.valueOf(adminUserId);
        User admin = userMapper.selectById(adminUserId);
        if (admin != null && StringUtils.hasText(admin.getNickname())) {
            creator = admin.getNickname().trim();
        }

        SensitiveWord entity = new SensitiveWord();
        applySave(entity, request);
        entity.setCreator(creator);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        entity.setMatchType("NORMAL");
        entity.setVersion(1L);
        sensitiveWordMapper.insert(entity);
        publishReload();
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminSensitiveWordVO update(Long id, AdminSensitiveWordSaveRequestDTO request) {
        validateSave(request);
        SensitiveWord entity = requireEntity(id);
        String word = request.getWord().trim();
        assertWordUnique(word, id);
        applySave(entity, request);
        entity.setUpdateTime(LocalDateTime.now());
        entity.setVersion(entity.getVersion() == null ? 1L : entity.getVersion() + 1);
        sensitiveWordMapper.updateById(entity);
        publishReload();
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SensitiveWord entity = requireEntity(id);
        sensitiveWordMapper.deleteById(entity.getId());
        publishReload();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminSensitiveWordVO updateStatus(Long id, Integer status) {
        if (!Objects.equals(status, 0) && !Objects.equals(status, 1)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "status 仅支持 0 或 1");
        }
        SensitiveWord entity = requireEntity(id);
        entity.setStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        entity.setVersion(entity.getVersion() == null ? 1L : entity.getVersion() + 1);
        sensitiveWordMapper.updateById(entity);
        publishReload();
        return toVO(entity);
    }

    private void applySave(SensitiveWord entity, AdminSensitiveWordSaveRequestDTO request) {
        entity.setWord(request.getWord().trim());
        entity.setCategory(request.getCategory().trim().toUpperCase(Locale.ROOT));
        String level = request.getLevel() == null ? "" : request.getLevel().trim().toUpperCase(Locale.ROOT);
        entity.setRiskLevel(level);
        entity.setReplacement("NORMAL".equals(level) && StringUtils.hasText(request.getReplacement())
                ? request.getReplacement().trim() : null);
        entity.setMatchType("NORMAL");
        if (request.getStatus() != null) {
            if (!Objects.equals(request.getStatus(), 0) && !Objects.equals(request.getStatus(), 1)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "status 仅支持 0 或 1");
            }
            entity.setStatus(request.getStatus());
        }
    }

    private void validateSave(AdminSensitiveWordSaveRequestDTO request) {
        if (request == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请求体不能为空");
        }
        if (!StringUtils.hasText(request.getWord())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "敏感词不能为空");
        }
        String category = request.getCategory() == null ? "" : request.getCategory().trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "category 不合法");
        }
        String level = request.getLevel() == null ? "" : request.getLevel().trim().toUpperCase(Locale.ROOT);
        if (!LEVELS.contains(level)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "level 仅支持 NORMAL、REVIEW、BLOCK");
        }
    }

    private void assertWordUnique(String word, Long excludeId) {
        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getWord, word);
        if (excludeId != null) {
            wrapper.ne(SensitiveWord::getId, excludeId);
        }
        Long count = sensitiveWordMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "敏感词已存在");
        }
    }

    private SensitiveWord requireEntity(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "id 不合法");
        }
        SensitiveWord entity = sensitiveWordMapper.selectById(id);
        if (entity == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "敏感词不存在");
        }
        return entity;
    }

    private void publishReload() {
        if (versionMapper.incrementVersion() != 1) {
            throw new BizException(Result.CODE_ERROR, "更新敏感词词库版本失败");
        }
        Long version = versionMapper.selectVersion();
        if (version == null) {
            throw new BizException(Result.CODE_ERROR, "读取敏感词词库版本失败");
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishVersion(version);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publishVersion(version);
            }
        });
    }

    private void publishVersion(Long version) {
        try {
            stringRedisTemplate.convertAndSend(SensitiveWordFilterService.RELOAD_CHANNEL, String.valueOf(version));
        } catch (Exception exception) {
            // 中文注释：数据库版本已提交，Redis 通知失败由各实例定时版本核对补偿。
            log.warn("发布敏感词词库刷新通知失败, version={}", version, exception);
        }
    }

    private AdminSensitiveWordVO toVO(SensitiveWord entity) {
        return adminViewMapper.toSensitiveWordVO(entity);
    }
}
