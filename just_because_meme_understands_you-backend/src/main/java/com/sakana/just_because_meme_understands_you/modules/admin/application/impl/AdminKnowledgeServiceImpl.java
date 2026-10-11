package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.pagination.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminKnowledgeSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.AdminKnowledge;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.AdminKnowledgeMapper;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminKnowledgeService;
import com.sakana.just_because_meme_understands_you.modules.admin.application.assembler.AdminViewMapper;
import com.sakana.just_because_meme_understands_you.modules.admin.application.support.KnowledgeDocumentParseSupport;
import com.sakana.just_because_meme_understands_you.modules.admin.application.support.KnowledgeUploadProperties;
import com.sakana.just_because_meme_understands_you.modules.ai.application.support.AiContentHashSupport;
import com.sakana.just_because_meme_understands_you.modules.integration.outbox.OutboxEventWriter;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminKnowledgeVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminKnowledgeServiceImpl implements IAdminKnowledgeService {

    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_DELETED = 0;
    // 管理知识库的 DATETIME 统一按中国标准时间生成，避免依赖运行机器默认时区。
    private static final ZoneId KNOWLEDGE_TIME_ZONE = ZoneId.of("Asia/Shanghai");

    @Resource
    private AdminKnowledgeMapper adminKnowledgeMapper;

    @Resource
    private AdminViewMapper adminViewMapper;

    @Resource
    private OutboxEventWriter outboxEventWriter;

    /** 中文注释：统一校验手动录入和文件解析后的知识正文上限。 */
    @Resource
    private KnowledgeUploadProperties knowledgeUploadProperties = new KnowledgeUploadProperties();

    /** 中文注释：文件解析在事务外完成，正文与 outbox 事件仍在短事务内原子保存。 */
    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public PageVO<AdminKnowledgeVO> page(Integer page, Integer size, String keyword, String category) {
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        LambdaQueryWrapper<AdminKnowledge> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w.eq(AdminKnowledge::getStatus, STATUS_PUBLISHED)
                .or(deleted -> deleted.eq(AdminKnowledge::getStatus, STATUS_DELETED)
                        .in(AdminKnowledge::getIndexStatus, Arrays.asList("FAILED", "DELETE_PENDING"))));
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
    public AdminKnowledgeVO createFromFile(Long adminUserId,
                                           MultipartFile file,
                                           String title,
                                           String category,
                                           List<String> tags) {
        KnowledgeDocumentParseSupport.ParsedKnowledgeDocument parsed =
                KnowledgeDocumentParseSupport.parse(file, knowledgeUploadProperties);
        AdminKnowledgeSaveRequestDTO request = new AdminKnowledgeSaveRequestDTO();
        request.setTitle(StringUtils.hasText(title) ? title.trim() : parsed.title());
        request.setContent(parsed.content());
        request.setCategory(category);
        request.setTags(tags == null ? Collections.emptyList() : tags);
        validate(request);
        return transactionTemplate.execute(status -> persistAndIngest(adminUserId, request));
    }

    private AdminKnowledgeVO persistAndIngest(Long adminUserId, AdminKnowledgeSaveRequestDTO request) {
        long startedAt = System.nanoTime();
        List<String> tags = normalizeTags(request.getTags());
        String content = AiContentHashSupport.normalizeKnowledgeBody(request.getContent()).trim();
        String contentSha256 = AiContentHashSupport.knowledgeBodySha256(content);

        LocalDateTime now = LocalDateTime.now(KNOWLEDGE_TIME_ZONE);
        AdminKnowledge entity = new AdminKnowledge();
        entity.setId(IdWorker.getId());
        entity.setTitle(request.getTitle().trim());
        entity.setContent(content);
        entity.setCategory(trimToNull(request.getCategory()));
        entity.setTags(joinTags(tags));
        entity.setContentHash(AiContentHashSupport.knowledgeHash(
                request.getTitle(), request.getCategory(), content, tags));
        entity.setContentSha256(contentSha256);
        entity.setStatus(STATUS_PUBLISHED);
        entity.setIndexStatus("PENDING");
        entity.setIndexRevision(1L);
        entity.setCreatorId(adminUserId);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        // 中文注释：直接依赖唯一索引插入，避免先查空再插入造成并发间隙锁死锁。
        if (adminKnowledgeMapper.insertIfContentSha256Absent(entity) == 0) {
            // 中文注释：INSERT IGNORE 等待并发赢家提交后，当前读取得其文档并复用任务。
            AdminKnowledge existing = adminKnowledgeMapper.selectByContentSha256ForUpdate(contentSha256);
            if (existing == null) {
                throw new IllegalStateException("知识正文唯一键冲突后未找到已提交文档");
            }
            log.info("Knowledge document deduplicated chars={} elapsed_ms={}",
                    content.length(), (System.nanoTime() - startedAt) / 1_000_000L);
            return reuseExisting(existing);
        }

        publishIndexEvent(entity, "KNOWLEDGE_INDEX_UPSERT");
        log.info("Knowledge document saved doc_id={} chars={} elapsed_ms={}",
                entity.getId(), content.length(), (System.nanoTime() - startedAt) / 1_000_000L);
        return toVO(entity);
    }

    private AdminKnowledgeVO reuseExisting(AdminKnowledge existing) {
        if (Objects.equals(existing.getStatus(), STATUS_DELETED)) {
            existing.setStatus(STATUS_PUBLISHED);
            existing.setIndexRevision(nextRevision(existing));
            existing.setIndexStatus("PENDING");
            existing.setIndexError(null);
            existing.setUpdateTime(LocalDateTime.now(KNOWLEDGE_TIME_ZONE));
            adminKnowledgeMapper.updateById(existing);
            publishIndexEvent(existing, "KNOWLEDGE_INDEX_UPSERT");
        } else if ("FAILED".equals(existing.getIndexStatus())) {
            queueUpsert(existing);
        }
        AdminKnowledgeVO duplicate = toVO(existing);
        duplicate.setDuplicate(true);
        return duplicate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminKnowledgeVO update(Long id, AdminKnowledgeSaveRequestDTO request) {
        validate(request);
        AdminKnowledge entity = requirePublishedForUpdate(id);
        List<String> tags = normalizeTags(request.getTags());
        String content = AiContentHashSupport.normalizeKnowledgeBody(request.getContent()).trim();
        String contentSha256 = AiContentHashSupport.knowledgeBodySha256(content);
        AdminKnowledge duplicate = adminKnowledgeMapper.selectByContentSha256ForUpdate(contentSha256);
        if (duplicate != null && !Objects.equals(duplicate.getId(), entity.getId())) {
            throw new BizException(Result.CODE_BAD_REQUEST,
                    "正文与知识文档 " + duplicate.getId() + " 重复，请编辑已有文档或更换正文");
        }

        entity.setTitle(request.getTitle().trim());
        entity.setContent(content);
        entity.setCategory(trimToNull(request.getCategory()));
        entity.setTags(joinTags(tags));
        entity.setContentHash(AiContentHashSupport.knowledgeHash(
                request.getTitle(), request.getCategory(), content, tags));
        entity.setContentSha256(contentSha256);
        entity.setIndexRevision(nextRevision(entity));
        entity.setIndexStatus("PENDING");
        entity.setIndexError(null);
        entity.setUpdateTime(LocalDateTime.now(KNOWLEDGE_TIME_ZONE));
        adminKnowledgeMapper.updateById(entity);

        publishIndexEvent(entity, "KNOWLEDGE_INDEX_UPSERT");
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminKnowledgeVO delete(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "id 不合法");
        }
        AdminKnowledge entity = adminKnowledgeMapper.selectByIdForUpdate(id);
        if (entity == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "知识文档不存在");
        }
        if (Objects.equals(entity.getStatus(), STATUS_DELETED)
                && !"FAILED".equals(entity.getIndexStatus())) {
            return toVO(entity);
        }
        entity.setStatus(STATUS_DELETED);
        entity.setIndexRevision(nextRevision(entity));
        entity.setIndexStatus("DELETE_PENDING");
        entity.setIndexError(null);
        entity.setUpdateTime(LocalDateTime.now(KNOWLEDGE_TIME_ZONE));
        adminKnowledgeMapper.updateById(entity);
        publishIndexEvent(entity, "KNOWLEDGE_INDEX_DELETE");
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminKnowledgeVO reindex(Long id) {
        AdminKnowledge entity = requirePublishedForUpdate(id);
        queueUpsert(entity);
        return toVO(entity);
    }

    @Override
    public AdminKnowledgeVO taskStatus(Long id, Long taskId) {
        if (id == null || id <= 0 || taskId == null || taskId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "任务标识不合法");
        }
        AdminKnowledge entity = adminKnowledgeMapper.selectById(id);
        if (entity == null || !Objects.equals(entity.getIndexTaskId(), taskId)) {
            throw new BizException(Result.CODE_NOT_FOUND, "向量任务不存在或已被新任务替代");
        }
        return toVO(entity);
    }

    private void queueUpsert(AdminKnowledge entity) {
        entity.setIndexRevision(nextRevision(entity));
        entity.setIndexStatus("PENDING");
        entity.setIndexError(null);
        entity.setUpdateTime(LocalDateTime.now(KNOWLEDGE_TIME_ZONE));
        adminKnowledgeMapper.updateById(entity);
        publishIndexEvent(entity, "KNOWLEDGE_INDEX_UPSERT");
    }

    private void publishIndexEvent(AdminKnowledge entity, String eventType) {
        String operation = "KNOWLEDGE_INDEX_DELETE".equals(eventType) ? "DELETE" : "UPSERT";
        String payload = "{\"docId\":\"" + entity.getId() + "\",\"revision\":"
                + entity.getIndexRevision() + ",\"operation\":\"" + operation + "\"}";
        Long taskId = outboxEventWriter.appendEvent(
                "ADMIN_KNOWLEDGE", entity.getId(), eventType, payload);
        entity.setIndexTaskId(taskId);
        adminKnowledgeMapper.updateById(entity);
    }

    private static long nextRevision(AdminKnowledge entity) {
        return entity.getIndexRevision() == null ? 1L : entity.getIndexRevision() + 1L;
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
        if (request.getContent().length() > knowledgeUploadProperties.getMaxContentChars()) {
            throw new BizException(Result.CODE_BAD_REQUEST,
                    "知识正文超过 " + knowledgeUploadProperties.getMaxContentChars()
                            + " 个字符，请拆分后再提交");
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

    private AdminKnowledge requirePublishedForUpdate(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "id 不合法");
        }
        AdminKnowledge entity = adminKnowledgeMapper.selectByIdForUpdate(id);
        if (entity == null || !Objects.equals(entity.getStatus(), STATUS_PUBLISHED)) {
            throw new BizException(Result.CODE_NOT_FOUND, "知识文档不存在");
        }
        return entity;
    }

    private AdminKnowledgeVO toVO(AdminKnowledge entity) {
        AdminKnowledgeVO vo = adminViewMapper.toAdminKnowledgeVO(entity);
        vo.setTags(parseTags(entity.getTags()));
        if (vo.getDuplicate() == null) {
            vo.setDuplicate(false);
        }
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
