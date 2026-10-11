package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.pagination.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminHomeImageSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.home.domain.entity.HomeImage;
import com.sakana.just_because_meme_understands_you.modules.home.infrastructure.persistence.mapper.HomeImageMapper;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminHomeImageService;
import com.sakana.just_because_meme_understands_you.modules.admin.application.assembler.AdminViewMapper;
import com.sakana.just_because_meme_understands_you.modules.home.application.IHomeImageService;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssObjectPromoteService;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminHomeImageVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class AdminHomeImageServiceImpl implements IAdminHomeImageService {

    @Resource
    private HomeImageMapper homeImageMapper;

    @Resource
    private AdminViewMapper adminViewMapper;

    @Resource
    private IHomeImageService homeImageService;

    @Resource
    private OssObjectPromoteService ossObjectPromoteService;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Resource
    private MemeCatalogPort memeCatalogPort;

    @Override
    public PageVO<AdminHomeImageVO> page(Integer page, Integer size, Integer status) {
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        LambdaQueryWrapper<HomeImage> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(HomeImage::getStatus, status);
        }
        wrapper.orderByDesc(HomeImage::getSortOrder).orderByDesc(HomeImage::getId);

        Page<HomeImage> mpPage = homeImageMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<HomeImage> records = mpPage.getRecords() != null ? mpPage.getRecords() : Collections.emptyList();

        PageVO<AdminHomeImageVO> vo = new PageVO<>();
        vo.setList(records.stream().map(this::toVO).collect(Collectors.toList()));
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setTotal(mpPage.getTotal());
        vo.setHasMore(mpPage.getCurrent() * mpPage.getSize() < mpPage.getTotal());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminHomeImageVO create(Long adminUserId, AdminHomeImageSaveRequestDTO request) {
        validateSave(request);
        HomeImage entity = new HomeImage();
        applySave(entity, request, adminUserId);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }
        homeImageMapper.insert(entity);
        homeImageService.evictCarouselCache();
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminHomeImageVO update(Long adminUserId, Long id, AdminHomeImageSaveRequestDTO request) {
        validateSave(request);
        HomeImage entity = requireEntity(id);
        applySave(entity, request, adminUserId);
        entity.setUpdateTime(LocalDateTime.now());
        homeImageMapper.updateById(entity);
        homeImageService.evictCarouselCache();
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        HomeImage entity = requireEntity(id);
        if (!isImageReferencedElsewhere(entity)) {
            ossObjectPromoteService.deleteHomeImage(entity.getImgUrl());
        }
        homeImageMapper.deleteById(entity.getId());
        homeImageService.evictCarouselCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminHomeImageVO updateStatus(Long id, Integer status) {
        if (!Objects.equals(status, 0) && !Objects.equals(status, 1)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "status 仅支持 0 或 1");
        }
        HomeImage entity = requireEntity(id);
        entity.setStatus(status);
        entity.setUpdateTime(LocalDateTime.now());
        homeImageMapper.updateById(entity);
        homeImageService.evictCarouselCache();
        return toVO(entity);
    }

    private void applySave(HomeImage entity, AdminHomeImageSaveRequestDTO request, Long adminUserId) {
        entity.setTitle(trimToNull(request.getTitle()));
        entity.setImgUrl(ossObjectPromoteService.promoteHomeImage(request.getImgUrl(), adminUserId));
        entity.setTargetType(request.getTargetType());
        entity.setTargetValue(normalizeTargetValue(request.getTargetType(), request.getTargetValue()));
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            if (!Objects.equals(request.getStatus(), 0) && !Objects.equals(request.getStatus(), 1)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "status 仅支持 0 或 1");
            }
            entity.setStatus(request.getStatus());
        }
    }

    private void validateSave(AdminHomeImageSaveRequestDTO request) {
        if (request == null) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请求体不能为空");
        }
        Integer targetType = request.getTargetType();
        if (targetType == null || targetType < 0 || targetType > 2) {
            throw new BizException(Result.CODE_BAD_REQUEST, "targetType 仅支持 0/1/2");
        }
        if (targetType == 1 || targetType == 2) {
            if (!StringUtils.hasText(request.getTargetValue())) {
                throw new BizException(Result.CODE_BAD_REQUEST, "跳转目标不能为空");
            }
        }
        if (targetType == 2) {
            String value = request.getTargetValue().trim().toLowerCase(Locale.ROOT);
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                throw new BizException(Result.CODE_BAD_REQUEST, "外部链接仅支持 http/https");
            }
        }
    }

    private String normalizeTargetValue(Integer targetType, String raw) {
        if (targetType == null || targetType == 0) {
            return null;
        }
        return raw == null ? null : raw.trim();
    }

    private HomeImage requireEntity(Long id) {
        if (id == null || id <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "id 不合法");
        }
        HomeImage entity = homeImageMapper.selectById(id);
        if (entity == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "轮播图不存在");
        }
        return entity;
    }

    private AdminHomeImageVO toVO(HomeImage entity) {
        AdminHomeImageVO vo = adminViewMapper.toAdminHomeImageVO(entity);
        vo.setImgUrl(ossUrlHelper.toPublicUrl(entity.getImgUrl()));
        return vo;
    }

    private boolean isImageReferencedElsewhere(HomeImage entity) {
        String objectKey = ossUrlHelper.normalizeForStorage(entity.getImgUrl());
        if (!StringUtils.hasText(objectKey) || !objectKey.startsWith("home/")) {
            return false;
        }
        LambdaQueryWrapper<HomeImage> otherCarouselReferences = new LambdaQueryWrapper<HomeImage>()
                .ne(HomeImage::getId, entity.getId())
                .and(wrapper -> wrapper.eq(HomeImage::getImgUrl, objectKey)
                        .or().eq(HomeImage::getImgUrl, entity.getImgUrl()));
        Long carouselReferenceCount = homeImageMapper.selectCount(otherCarouselReferences);
        if (carouselReferenceCount != null && carouselReferenceCount > 0) {
            log.info("轮播图删除后仍有其他轮播图引用图片，保留 OSS 对象 key={}", objectKey);
            return true;
        }
        if (memeCatalogPort.isImageAssetReferenced(objectKey)) {
            log.info("轮播图删除后仍有梗图内容引用图片，保留 OSS 对象 key={}", objectKey);
            return true;
        }
        return false;
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
