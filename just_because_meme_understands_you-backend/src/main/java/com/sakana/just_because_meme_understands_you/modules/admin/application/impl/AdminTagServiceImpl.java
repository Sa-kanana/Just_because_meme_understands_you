package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.common.pagination.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminTagSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTagRelation;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.MemeTagCategory;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminTagService;
import com.sakana.just_because_meme_understands_you.modules.admin.application.assembler.AdminViewMapper;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminTagVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminTagServiceImpl implements IAdminTagService {

    @Resource
    private MemeTagMapper memeTagMapper;

    @Resource
    private AdminViewMapper adminViewMapper;

    @Resource
    private MemeTagRelationMapper memeTagRelationMapper;

    @Override
    public PageVO<AdminTagVO> page(Integer page, Integer size, String keyword, String category) {
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        LambdaQueryWrapper<MemeTag> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(MemeTag::getName, keyword.trim());
        }
        if (StringUtils.hasText(category)) {
            String categoryCode = category.trim().toUpperCase(java.util.Locale.ROOT);
            if (!MemeTagCategory.supports(categoryCode)
                    && !MemeTagCategory.UNCATEGORIZED.getCode().equals(categoryCode)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "标签分类不合法");
            }
            wrapper.eq(MemeTag::getCategory, categoryCode);
        }
        wrapper.orderByAsc(MemeTag::getCategory).orderByAsc(MemeTag::getSortOrder).orderByAsc(MemeTag::getId);

        Page<MemeTag> mpPage = memeTagMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<MemeTag> records = mpPage.getRecords() != null ? mpPage.getRecords() : Collections.emptyList();

        PageVO<AdminTagVO> vo = new PageVO<>();
        vo.setList(records.stream().map(this::toVO).collect(Collectors.toList()));
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setTotal(mpPage.getTotal());
        vo.setHasMore(mpPage.getCurrent() * mpPage.getSize() < mpPage.getTotal());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminTagVO create(AdminTagSaveRequestDTO request) {
        String name = requireName(request);
        String category = requireCategory(request, false);
        assertNameUnique(name, null);
        MemeTag tag = new MemeTag();
        tag.setName(name);
        tag.setCategory(category);
        tag.setSortOrder(request.getSortOrder() == null ? 0 : Math.max(request.getSortOrder(), 0));
        tag.setRelatedQuantity(0);
        memeTagMapper.insert(tag);
        return toVO(tag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminTagVO update(Integer id, AdminTagSaveRequestDTO request) {
        MemeTag tag = requireTag(id);
        String name = requireName(request);
        String category = requireCategory(request, true);
        assertNameUnique(name, id);
        tag.setName(name);
        tag.setCategory(category);
        if (request.getSortOrder() != null) {
            tag.setSortOrder(Math.max(request.getSortOrder(), 0));
        }
        memeTagMapper.updateById(tag);
        return toVO(tag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer id) {
        MemeTag tag = requireTag(id);
        memeTagRelationMapper.delete(new LambdaQueryWrapper<MemeTagRelation>()
                .eq(MemeTagRelation::getMemeTagId, tag.getId()));
        memeTagMapper.deleteById(tag.getId());
    }

    private String requireName(AdminTagSaveRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BizException(Result.CODE_BAD_REQUEST, "标签名不能为空");
        }
        return request.getName().trim();
    }

    /** 中文注释：拒绝未登记的分类代码，防止写入无法被用户端分组的标签。 */
    private String requireCategory(AdminTagSaveRequestDTO request, boolean allowUncategorized) {
        String category = request == null || request.getCategory() == null
                ? "" : request.getCategory().trim().toUpperCase(java.util.Locale.ROOT);
        boolean isUncategorized = MemeTagCategory.UNCATEGORIZED.getCode().equals(category);
        if (!MemeTagCategory.supports(category) && !(allowUncategorized && isUncategorized)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "请选择有效标签分类");
        }
        return category;
    }

    private void assertNameUnique(String name, Integer excludeId) {
        LambdaQueryWrapper<MemeTag> wrapper = new LambdaQueryWrapper<MemeTag>()
                .eq(MemeTag::getName, name);
        if (excludeId != null) {
            wrapper.ne(MemeTag::getId, excludeId);
        }
        Long count = memeTagMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "标签名已存在");
        }
    }

    private MemeTag requireTag(Integer id) {
        if (id == null || id <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "id 不合法");
        }
        MemeTag tag = memeTagMapper.selectById(id);
        if (tag == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    private AdminTagVO toVO(MemeTag tag) {
        return adminViewMapper.toTagVO(tag);
    }
}
