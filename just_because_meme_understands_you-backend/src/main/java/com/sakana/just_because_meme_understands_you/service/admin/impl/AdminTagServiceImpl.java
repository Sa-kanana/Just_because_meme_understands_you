package com.sakana.just_because_meme_understands_you.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.dto.admin.AdminTagSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.entity.MemeTagRelation;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagRelationMapper;
import com.sakana.just_because_meme_understands_you.service.admin.IAdminTagService;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminTagVO;
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
    private MemeTagRelationMapper memeTagRelationMapper;

    @Override
    public PageVO<AdminTagVO> page(Integer page, Integer size, String keyword) {
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        LambdaQueryWrapper<MemeTag> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(MemeTag::getName, keyword.trim());
        }
        wrapper.orderByDesc(MemeTag::getId);

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
        assertNameUnique(name, null);
        MemeTag tag = new MemeTag();
        tag.setName(name);
        tag.setRelatedQuantity("0");
        memeTagMapper.insert(tag);
        return toVO(tag);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminTagVO update(Integer id, AdminTagSaveRequestDTO request) {
        MemeTag tag = requireTag(id);
        String name = requireName(request);
        assertNameUnique(name, id);
        tag.setName(name);
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
        AdminTagVO vo = new AdminTagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setRelatedQuantity(tag.getRelatedQuantity());
        return vo;
    }
}
