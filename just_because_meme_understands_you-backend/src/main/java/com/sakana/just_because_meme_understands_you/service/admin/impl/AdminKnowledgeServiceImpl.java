package com.sakana.just_because_meme_understands_you.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.dto.admin.AdminKnowledgeSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.AdminKnowledge;
import com.sakana.just_because_meme_understands_you.mapper.AdminKnowledgeMapper;
import com.sakana.just_because_meme_understands_you.service.admin.IAdminKnowledgeService;
import com.sakana.just_because_meme_understands_you.service.admin.support.KnowledgeDocumentParseSupport;
import com.sakana.just_because_meme_understands_you.service.ai.application.KnowledgeIngestPublisher;
import com.sakana.just_because_meme_understands_you.service.ai.support.AiContentHashSupport;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminKnowledgeVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminKnowledgeServiceImpl implements IAdminKnowledgeService {

    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_DELETED = 0;

    @Resource
    private AdminKnowledgeMapper adminKnowledgeMapper;

    @Resource
    private KnowledgeIngestPublisher knowledgeIngestPublisher;

    @Override
    public PageVO<AdminKnowledgeVO> page(Integer page, Integer size, String keyword, String category) {
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        LambdaQueryWrapper<AdminKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AdminKnowledge::getStatus, STATUS_PUBLISHED);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(AdminKnowledge::getTitle, kw).or().like(AdminKnowledge::getContent, kw));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(AdminKnowledge::getCategory, category.trim());
        }
        wrapper.orderByDesc(AdminKnowledge::getUpdateTime).orderByDesc(AdminKnowledge::getId);

        Page<AdminKnowledge> mpPage = adminKnowledgeMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<AdminKnowledge> records = mpPage.getRecords() != null ? mpPage.getRecords() : Collections.emptyList();

        PageVO<AdminKnowledgeVO> vo = new PageVO<>();
        vo.setList(records.stream().map(this::toVO).collect(Collectors.toList()));
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setTotal(mpPage.getTotal());
        vo.setHasMore(mpPage.getCurrent() * mpPage.getSize() < mpPage.getTotal());
        return vo;
    }

    @Override
    public AdminKnowledgeVO detail(Long id) {
        return toVO(requirePublished(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminKnowledgeVO create(Long adminUserId, AdminKnowledgeSaveRequestDTO request) {
        validate(request);
        return persistAndIngest(adminUserId, request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminKnowledgeVO createFromFile(Long adminUserId,
                                           MultipartFile file,
                                           String title,
                                           String category,
                                           List<String> tags) {
        KnowledgeDocumentParseSupport.ParsedKnowledgeDocument parsed =
                KnowledgeDocumentParseSupport.parse(file);
        AdminKnowledgeSaveRequestDTO request = new AdminKnowledgeSaveRequestDTO();
        request.setTitle(StringUtils.hasText(title) ? title.trim() : parsed.title());
        request.setContent(parsed.content());
        request.setCategory(category);
        request.setTags(tags == null ? Collections.emptyList() : tags);
        validate(request);
        return persistAndIngest(adminUserId, request);
    }

    private AdminKnowledgeVO persistAndIngest(Long adminUserId, AdminKnowledgeSaveRequestDTO request) {
        List<String> tags = normalizeTags(request.getTags());
        String hash = AiContentHashSupport.knowledgeHash(
                request.getTitle(), request.getCategory(), request.getContent(), tags);

        LocalDateTime now = LocalDateTime.now();
        AdminKnowledge entity = new AdminKnowledge();
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent().trim());
        entity.setCategory(trimToNull(request.getCategory()));
        entity.setTags(joinTags(tags));
        entity.setContentHash(hash);
        entity.setStatus(STATUS_PUBLISHED);
        entity.setCreatorId(adminUserId);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        adminKnowledgeMapper.insert(entity);

        knowledgeIngestPublisher.publishPublished(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminKnowledgeVO update(Long id, AdminKnowledgeSaveRequestDTO request) {
        validate(request);
        AdminKnowledge entity = requirePublished(id);
        List<String> tags = normalizeTags(request.getTags());
        String hash = AiContentHashSupport.knowledgeHash(
                request.getTitle(), request.getCategory(), request.getContent(), tags);

        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent().trim());
        entity.setCategory(trimToNull(request.getCategory()));
        entity.setTags(joinTags(tags));
        entity.setContentHash(hash);
        entity.setUpdateTime(LocalDateTime.now());
        adminKnowledgeMapper.updateById(entity);

        knowledgeIngestPublisher.publishPublished(entity);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AdminKnowledge entity = requirePublished(id);
        entity.setStatus(STATUS_DELETED);
        entity.setUpdateTime(LocalDateTime.now());
        adminKnowledgeMapper.updateById(entity);
        knowledgeIngestPublisher.publishDeleted(entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminKnowledgeVO reindex(Long id) {
        AdminKnowledge entity = requirePublished(id);
        entity.setUpdateTime(LocalDateTime.now());
        adminKnowledgeMapper.updateById(entity);
        knowledgeIngestPublisher.publishPublished(entity);
        return toVO(entity);
    }

    private void validate(AdminKnowledgeSaveRequestDTO request) {
        if (request == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请求体不能为空");
        }
        if (!StringUtils.hasText(request.getTitle())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "标题不能为空");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "正文不能为空");
        }
    }

    private AdminKnowledge requirePublished(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "id 不合法");
        }
        AdminKnowledge entity = adminKnowledgeMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getStatus(), STATUS_PUBLISHED)) {
            throw new BizException(Result.CODE_NOT_FOUND, "知识文档不存在");
        }
        return entity;
    }

    private AdminKnowledgeVO toVO(AdminKnowledge entity) {
        AdminKnowledgeVO vo = new AdminKnowledgeVO();
        vo.setId(entity.getId());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setCategory(entity.getCategory());
        vo.setTags(parseTags(entity.getTags()));
        vo.setContentHash(entity.getContentHash());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private static List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.emptyList();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(20)
                .collect(Collectors.toList());
    }

    private static String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return String.join(",", tags);
    }

    private static List<String> parseTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
