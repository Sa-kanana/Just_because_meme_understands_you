package com.sakana.just_because_meme_understands_you.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.dto.admin.AdminSensitiveWordSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.SensitiveWord;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.mapper.SensitiveWordMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserMapper;
import com.sakana.just_because_meme_understands_you.service.admin.IAdminSensitiveWordService;
import com.sakana.just_because_meme_understands_you.service.comment.SensitiveWordFilterService;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminSensitiveWordVO;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminSensitiveWordServiceImpl implements IAdminSensitiveWordService {

    private static final Set<Integer> ACTION_TYPES = Set.of(1, 2, 3);
    private static final Set<String> CATEGORIES = Set.of("POLITICS", "PORN", "ABUSE", "AD", "CUSTOM");

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public PageVO<AdminSensitiveWordVO> page(Integer page, Integer size, String keyword, String category, Integer status) {
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        LambdaQueryWrapper<SensitiveWord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SensitiveWord::getWord, keyword.trim());
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(SensitiveWord::getCategory, category.trim().toUpperCase(Locale.ROOT));
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
        sensitiveWordMapper.updateById(entity);
        publishReload();
        return toVO(entity);
    }

    private void applySave(SensitiveWord entity, AdminSensitiveWordSaveRequestDTO request) {
        entity.setWord(request.getWord().trim());
        entity.setCategory(request.getCategory().trim().toUpperCase(Locale.ROOT));
        entity.setActionType(request.getActionType());
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
        if (!ACTION_TYPES.contains(request.getActionType())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "actionType 仅支持 1/2/3");
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
        stringRedisTemplate.convertAndSend(SensitiveWordFilterService.RELOAD_CHANNEL, "reload");
    }

    private AdminSensitiveWordVO toVO(SensitiveWord entity) {
        AdminSensitiveWordVO vo = new AdminSensitiveWordVO();
        vo.setId(entity.getId());
        vo.setWord(entity.getWord());
        vo.setCategory(entity.getCategory());
        vo.setActionType(entity.getActionType());
        vo.setStatus(entity.getStatus());
        vo.setCreator(entity.getCreator());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
