package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.pagination.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminMemeCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.dto.MemeCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCreateResponseVO;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.user.domain.UserDisplayPolicy;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserMapper;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminMemeService;
import com.sakana.just_because_meme_understands_you.modules.admin.application.assembler.AdminViewMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.application.MemeApproveService;
import com.sakana.just_because_meme_understands_you.modules.meme.application.MemeDeleteService;
import com.sakana.just_because_meme_understands_you.modules.meme.application.MemePublishService;
import com.sakana.just_because_meme_understands_you.modules.meme.application.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.MemeResourceType;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeActionVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeDetailVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeListItemVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeResourceVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeTagVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminMemeServiceImpl implements IAdminMemeService {

    @Resource
    private MemeMapper memeMapper;

    @Resource
    private AdminViewMapper adminViewMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private MemeApproveService memeApproveService;

    @Resource
    private MemeDeleteService memeDeleteService;

    @Resource
    private MemePublishService memePublishService;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Resource
    private MemeCatalogPort memeCatalogPort;

    /** 以当前管理员身份创建梗，并复用用户发布链路完成 OSS 转正和审核入库。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemeCreateResponseVO create(Long adminUserId, AdminMemeCreateRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "梗名称不能为空");
        }

        String name = request.getName().trim();
        LambdaQueryWrapper<Meme> duplicateQuery = new LambdaQueryWrapper<>();
        duplicateQuery.eq(Meme::getName, name)
                .ne(Meme::getStatus, MemeVisibilitySupport.STATUS_PURGED);
        Long duplicateCount = memeMapper.selectCount(duplicateQuery);
        if (duplicateCount != null && duplicateCount > 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "同名梗已存在");
        }

        MemeCreateRequestDTO publishRequest = new MemeCreateRequestDTO();
        publishRequest.setName(name);
        publishRequest.setIntroduction(request.getIntroduction());
        publishRequest.setImage(request.getImage());
        publishRequest.setTagIds(request.getTagIds());
        return memePublishService.publish(adminUserId, publishRequest);
    }

    @Override
    public PageVO<AdminMemeListItemVO> page(Integer page, Integer size, Integer status, String keyword, Long userId) {
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        LambdaQueryWrapper<Meme> wrapper = new LambdaQueryWrapper<>();
        wrapper.ne(Meme::getStatus, MemeVisibilitySupport.STATUS_PURGED);
        if (status != null) {
            wrapper.eq(Meme::getStatus, status);
        }
        if (userId != null && userId > 0) {
            wrapper.eq(Meme::getUserId, userId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Meme::getName, kw).or().like(Meme::getIntroduction, kw));
        }
        wrapper.orderByDesc(Meme::getUpdateTime).orderByDesc(Meme::getId);

        Page<Meme> mpPage = memeMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<Meme> records = mpPage.getRecords() != null ? mpPage.getRecords() : Collections.emptyList();
        Map<Long, String> nicknameMap = loadNicknames(records);

        PageVO<AdminMemeListItemVO> vo = new PageVO<>();
        vo.setList(records.stream().map(m -> toListItem(m, nicknameMap)).collect(Collectors.toList()));
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setTotal(mpPage.getTotal());
        vo.setHasMore(mpPage.getCurrent() * mpPage.getSize() < mpPage.getTotal());
        return vo;
    }

    @Override
    public AdminMemeDetailVO detail(Long memeId) {
        Meme meme = requireMeme(memeId);
        Map<Long, String> nicknameMap = loadNicknames(List.of(meme));
        return toDetail(meme, nicknameMap);
    }

    @Override
    public AdminMemeActionVO approve(Long memeId) {
        memeApproveService.approveToPublished(memeId);
        Meme meme = requireMeme(memeId);
        return toAction(meme);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMemeActionVO reject(Long memeId) {
        Meme meme = requireMeme(memeId);
        Meme updated;
        if (Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_REVIEWING)) {
            updated = memeDeleteService.rejectFirstReviewByAdmin(memeId, null);
        } else if (Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_RESTORE_REVIEWING)) {
            updated = memeDeleteService.rejectAppealByAdmin(memeId, null);
        } else {
            throw new BizException(Result.CODE_BAD_REQUEST, "仅审核中或恢复审核中的梗可拒绝");
        }
        return toAction(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMemeActionVO offline(Long memeId) {
        Meme updated = memeDeleteService.markLockedByAdmin(memeId, null);
        return toAction(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMemeActionVO online(Long memeId) {
        Meme updated = memeDeleteService.forceOnlineByAdmin(memeId);
        return toAction(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminMemeActionVO delete(Long memeId) {
        Meme deleted = memeDeleteService.purgeByAdmin(memeId);
        return toAction(deleted);
    }

    private Meme requireMeme(Long memeId) {
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不合法");
        }
        Meme meme = memeMapper.selectById(memeId);
        if (meme == null || Objects.equals(meme.getStatus(), MemeVisibilitySupport.STATUS_PURGED)) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在");
        }
        return meme;
    }

    private Map<Long, String> loadNicknames(List<Meme> memes) {
        Set<Long> userIds = memes.stream()
                .map(Meme::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, String> map = new HashMap<>();
        if (users != null) {
            for (User u : users) {
                if (u != null && u.getId() != null) {
                    map.put(u.getId(), UserDisplayPolicy.displayName(u, "匿名用户"));
                }
            }
        }
        return map;
    }

    private AdminMemeListItemVO toListItem(Meme meme, Map<Long, String> nicknameMap) {
        AdminMemeListItemVO vo = adminViewMapper.toAdminMemeListItemVO(meme);
        fillComputedFields(vo, meme, nicknameMap);
        return vo;
    }

    private AdminMemeDetailVO toDetail(Meme meme, Map<Long, String> nicknameMap) {
        AdminMemeDetailVO vo = adminViewMapper.toAdminMemeDetailVO(meme);
        fillComputedFields(vo, meme, nicknameMap);
        Long memeId = meme.getId();
        Map<Long, List<MemeCatalogPort.MemeTagSummary>> tagsByMemeId =
                memeCatalogPort.findTagsByMemeIds(List.of(memeId));
        List<MemeCatalogPort.MemeTagSummary> tagSummaries = tagsByMemeId == null
                ? List.of()
                : tagsByMemeId.getOrDefault(memeId, List.of());
        vo.setTags(tagSummaries.stream()
                .filter(Objects::nonNull)
                .map(tag -> {
                    AdminMemeTagVO detailTag = new AdminMemeTagVO();
                    detailTag.setId(tag.id());
                    detailTag.setName(tag.name());
                    detailTag.setRelatedQuantity(tag.relatedQuantity());
                    return detailTag;
                })
                .toList());

        List<MemeCatalogPort.MemeResourceSummary> resourceSummaries = memeCatalogPort.findResourcesByMemeId(memeId);
        vo.setResources(resourceSummaries == null ? List.of() : resourceSummaries.stream()
                .filter(Objects::nonNull)
                .map(resource -> {
                    AdminMemeResourceVO detailResource = new AdminMemeResourceVO();
                    detailResource.setId(resource.id());
                    detailResource.setType(MemeResourceType.fromCode(resource.resourceType()).getApiType());
                    detailResource.setTitle(resource.title());
                    detailResource.setUrl(resource.resourceUrl() == null || resource.resourceUrl().isBlank()
                            ? ""
                            : ossUrlHelper.toPublicUrl(resource.resourceUrl()));
                    detailResource.setSortOrder(resource.sortOrder());
                    detailResource.setStatus(resource.status());
                    return detailResource;
                })
                .toList());
        return vo;
    }

    private void fillComputedFields(AdminMemeListItemVO vo, Meme meme, Map<Long, String> nicknameMap) {
        vo.setImage(ossUrlHelper.toPublicUrl(meme.getImage()));
        vo.setStatusDesc(MemeVisibilitySupport.statusDesc(meme.getStatus()));
        vo.setAuthorNickname(meme.getUserId() == null ? null : nicknameMap.get(meme.getUserId()));
    }

    private void fillComputedFields(AdminMemeDetailVO vo, Meme meme, Map<Long, String> nicknameMap) {
        vo.setImage(ossUrlHelper.toPublicUrl(meme.getImage()));
        vo.setStatusDesc(MemeVisibilitySupport.statusDesc(meme.getStatus()));
        vo.setAuthorNickname(meme.getUserId() == null ? null : nicknameMap.get(meme.getUserId()));
    }

    private AdminMemeActionVO toAction(Meme meme) {
        AdminMemeActionVO vo = adminViewMapper.toAdminMemeActionVO(meme);
        vo.setStatusDesc(MemeVisibilitySupport.statusDesc(meme.getStatus()));
        return vo;
    }
}
