package com.sakana.just_because_meme_understands_you.modules.knowledge.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.pagination.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.knowledge.domain.MemeKnowledgeCard;
import com.sakana.just_because_meme_understands_you.modules.knowledge.domain.MemeKnowledgeCorrection;
import com.sakana.just_because_meme_understands_you.modules.knowledge.infrastructure.MemeKnowledgeCardMapper;
import com.sakana.just_because_meme_understands_you.modules.knowledge.infrastructure.MemeKnowledgeCorrectionMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MemeKnowledgeService {
    private static final Set<String> FIELDS = Set.of("sourceUrl", "firstSeenDate", "popularityContext",
            "usageExamples", "evolution", "relatedMemeIds");
    @Resource private MemeMapper memeMapper;
    @Resource private MemeKnowledgeCardMapper cardMapper;
    @Resource private MemeKnowledgeCorrectionMapper correctionMapper;
    @Resource private ObjectMapper objectMapper;

    public record RelatedMeme(Long id, String name) {}
    public record CardView(Long memeId, String sourceUrl, LocalDate firstSeenDate,
                           String popularityContext, String usageExamples, String evolution,
                           List<RelatedMeme> relatedMemes, LocalDateTime updatedAt) {}
    public record CorrectionView(Long id, Long memeId, String memeName, Long userId,
                                 Map<String, Object> changes, String status, Long handledBy,
                                 String handlingNote, LocalDateTime createdAt, LocalDateTime handledAt) {}

    private Meme requirePublic(Long memeId) {
        if (memeId == null || memeId <= 0) throw bad("梗 ID 不合法");
        Meme meme = memeMapper.selectById(memeId);
        if (meme == null || !Integer.valueOf(1).equals(meme.getStatus())) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或尚未公开");
        }
        return meme;
    }

    private BizException bad(String message) {
        return new BizException(Result.CODE_BAD_REQUEST, message);
    }

    public CardView publicCard(Long memeId) {
        requirePublic(memeId);
        MemeKnowledgeCard card = cardMapper.selectById(memeId);
        if (card == null) return new CardView(memeId, null, null, null, null, null, List.of(), null);
        List<RelatedMeme> related = new ArrayList<>();
        for (Long id : parseRelated(card.getRelatedMemeIds())) {
            Meme meme = memeMapper.selectById(id);
            if (meme != null && Integer.valueOf(1).equals(meme.getStatus())) {
                related.add(new RelatedMeme(id, meme.getName()));
            }
        }
        return new CardView(memeId, card.getSourceUrl(), card.getFirstSeenDate(),
                card.getPopularityContext(), card.getUsageExamples(), card.getEvolution(),
                related, card.getUpdatedAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long submit(Long memeId, Long userId, Map<String, Object> changes) {
        requirePublic(memeId);
        Map<String, Object> safe = validateChanges(memeId, changes);
        MemeKnowledgeCorrection row = new MemeKnowledgeCorrection();
        row.setMemeId(memeId);
        row.setUserId(userId);
        row.setChangesJson(toJson(safe));
        row.setStatus("PENDING");
        row.setCreatedAt(LocalDateTime.now());
        correctionMapper.insert(row);
        return row.getId();
    }

    public PageVO<CorrectionView> page(Integer page, Integer size, String status) {
        if (status != null && !status.isBlank() && !Set.of("PENDING", "APPROVED", "REJECTED").contains(status)) {
            throw bad("审核状态不合法");
        }
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);
        LambdaQueryWrapper<MemeKnowledgeCorrection> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) query.eq(MemeKnowledgeCorrection::getStatus, status);
        query.orderByDesc(MemeKnowledgeCorrection::getCreatedAt).orderByDesc(MemeKnowledgeCorrection::getId);
        Page<MemeKnowledgeCorrection> rows = correctionMapper.selectPage(new Page<>(pageNo, pageSize), query);
        PageVO<CorrectionView> output = new PageVO<>();
        output.setList(rows.getRecords().stream().map(this::view).toList());
        output.setPage(pageNo);
        output.setSize(pageSize);
        output.setTotal(rows.getTotal());
        output.setHasMore((long) pageNo * pageSize < rows.getTotal());
        return output;
    }

    @Transactional(rollbackFor = Exception.class)
    public CorrectionView resolve(Long id, Long adminId, String decision, String note) {
        if (id == null || id <= 0 || decision == null || !Set.of("APPROVED", "REJECTED").contains(decision)) throw bad("审核决定不合法");
        String safeNote = note == null ? "" : note.trim();
        if (safeNote.length() > 500) throw bad("审核备注最多 500 字");
        MemeKnowledgeCorrection row = correctionMapper.selectById(id);
        if (row == null) throw new BizException(Result.CODE_NOT_FOUND, "纠错申请不存在");
        Map<String, Object> changes = null;
        if ("APPROVED".equals(decision)) {
            requirePublic(row.getMemeId());
            changes = validateChanges(row.getMemeId(), parseChanges(row.getChangesJson()));
        }
        if (correctionMapper.resolvePending(id, decision, adminId, safeNote.isEmpty() ? null : safeNote,
                LocalDateTime.now()) != 1) throw bad("纠错申请已处理");
        if (changes != null) apply(row.getMemeId(), changes);
        return view(correctionMapper.selectById(id));
    }

    private CorrectionView view(MemeKnowledgeCorrection row) {
        Meme meme = memeMapper.selectById(row.getMemeId());
        return new CorrectionView(row.getId(), row.getMemeId(), meme == null ? null : meme.getName(),
                row.getUserId(), parseChanges(row.getChangesJson()), row.getStatus(), row.getHandledBy(),
                row.getHandlingNote(), row.getCreatedAt(), row.getHandledAt());
    }

    private Map<String, Object> validateChanges(Long memeId, Map<String, Object> changes) {
        if (changes == null || changes.isEmpty() || changes.size() > FIELDS.size() || !FIELDS.containsAll(changes.keySet())) {
            throw bad("纠错字段不合法");
        }
        Map<String, Object> safe = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : changes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if ("relatedMemeIds".equals(key)) {
                if (!(value instanceof List<?> list) || list.size() > 12) throw bad("相关梗最多 12 个");
                List<Long> ids = new ArrayList<>();
                for (Object raw : list) {
                    Long id;
                    try { id = Long.valueOf(String.valueOf(raw)); }
                    catch (NumberFormatException e) { throw bad("相关梗 ID 不合法"); }
                    if (id.equals(memeId) || ids.contains(id)) throw bad("相关梗不能重复或关联自身");
                    requirePublic(id);
                    ids.add(id);
                }
                safe.put(key, ids);
            } else {
                if (value != null && !(value instanceof String)) throw bad("纠错字段类型不合法");
                String text = value == null ? "" : ((String) value).trim();
                int limit = "sourceUrl".equals(key) ? 500 : "firstSeenDate".equals(key) ? 10 : 2000;
                if (text.length() > limit) throw bad("纠错内容超过长度限制");
                if ("sourceUrl".equals(key) && !text.isEmpty()) {
                    try {
                        URI uri = URI.create(text);
                        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                                || uri.getHost() == null || uri.getHost().isBlank()) throw bad("出处 URL 仅支持 http/https");
                    } catch (IllegalArgumentException e) { throw bad("出处 URL 不合法"); }
                }
                if ("firstSeenDate".equals(key) && !text.isEmpty()) {
                    try { LocalDate.parse(text); }
                    catch (DateTimeParseException e) { throw bad("首次出现日期格式须为 YYYY-MM-DD"); }
                }
                safe.put(key, text.isEmpty() ? null : text);
            }
        }
        return safe;
    }

    private void apply(Long memeId, Map<String, Object> changes) {
        MemeKnowledgeCard card = cardMapper.selectById(memeId);
        if (card == null) { card = new MemeKnowledgeCard(); card.setMemeId(memeId); card.setRelatedMemeIds("[]"); }
        if (changes.containsKey("sourceUrl")) card.setSourceUrl((String) changes.get("sourceUrl"));
        if (changes.containsKey("firstSeenDate")) {
            String date = (String) changes.get("firstSeenDate");
            card.setFirstSeenDate(date == null ? null : LocalDate.parse(date));
        }
        if (changes.containsKey("popularityContext")) card.setPopularityContext((String) changes.get("popularityContext"));
        if (changes.containsKey("usageExamples")) card.setUsageExamples((String) changes.get("usageExamples"));
        if (changes.containsKey("evolution")) card.setEvolution((String) changes.get("evolution"));
        if (changes.containsKey("relatedMemeIds")) card.setRelatedMemeIds(toJson(changes.get("relatedMemeIds")));
        cardMapper.upsert(card);
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new BizException("纠错内容序列化失败", e); }
    }

    private Map<String, Object> parseChanges(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (JsonProcessingException e) { throw new BizException("纠错内容读取失败", e); }
    }

    private List<Long> parseRelated(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (JsonProcessingException e) { return List.of(); }
    }
}
