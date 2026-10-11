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
import com.sakana.just_because_meme_understands_you.modules.oss.application.FileAssetService;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminHomeImageVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminHomeImageServiceImpl implements IAdminHomeImageService {

    @Resource
    private HomeImageMapper homeImageMapper;

    @Resource
    private AdminViewMapper adminViewMapper;

    @Resource
    private IHomeImageService homeImageService;

    /** 中文注释：提升轮播图上传并维护轮播图记录对应的文件资产引用。 */
    @Resource
    private FileAssetService fileAssetService;

    @Resource
    private OssUrlHelper ossUrlHelper;

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
        List<HomeImage> records = mpPage.getRecords() != null ? mpPage.getRecords() : List.of();

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
        if (homeImageMapper.insert(entity) != 1 || entity.getId() == null) {
            throw new BizException(Result.CODE_ERROR, "创建轮播图失败，请重试");
        }
        fileAssetService.replaceReference(entity.getImgUrl(), "BANNER", entity.getId(), "IMAGE", "BANNER_CREATED");
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
        if (homeImageMapper.updateById(entity) != 1) {
            throw new BizException(Result.CODE_ERROR, "更新轮播图失败，请重试");
        }
        fileAssetService.replaceReference(entity.getImgUrl(), "BANNER", entity.getId(), "IMAGE", "BANNER_UPDATED");
        homeImageService.evictCarouselCache();
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        HomeImage entity = requireEntity(id);
        fileAssetService.releaseReference("BANNER", entity.getId(), "IMAGE", "BANNER_DELETED");
        if (homeImageMapper.deleteById(entity.getId()) != 1) {
            throw new BizException(Result.CODE_ERROR, "删除轮播图失败，请重试");
        }
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
        entity.setImgUrl(fileAssetService.promote(request.getImgUrl(), adminUserId, "home"));
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

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
