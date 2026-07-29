package com.sakana.just_because_meme_understands_you.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.mapper.UserMapper;
import com.sakana.just_because_meme_understands_you.service.admin.IAdminMemeService;
import com.sakana.just_because_meme_understands_you.service.meme.MemeApproveService;
import com.sakana.just_because_meme_understands_you.service.meme.MemeDeleteService;
import com.sakana.just_because_meme_understands_you.service.meme.support.MemeVisibilitySupport;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminMemeActionVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminMemeDetailVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminMemeListItemVO;
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
    private UserMapper userMapper;

    @Resource
    private MemeApproveService memeApproveService;

    @Resource
    private MemeDeleteService memeDeleteService;

    @Resource
    private OssUrlHelper ossUrlHelper;

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
                    map.put(u.getId(), u.getNickname());
                }
            }
        }
        return map;
    }

    private AdminMemeListItemVO toListItem(Meme meme, Map<Long, String> nicknameMap) {
        AdminMemeListItemVO vo = new AdminMemeListItemVO();
        fillCommon(vo, meme, nicknameMap);
        return vo;
    }

    private AdminMemeDetailVO toDetail(Meme meme, Map<Long, String> nicknameMap) {
        AdminMemeDetailVO vo = new AdminMemeDetailVO();
        fillCommon(vo, meme, nicknameMap);
        vo.setOfflineReason(meme.getOfflineReason());
        vo.setAppealRejectCount(meme.getAppealRejectCount());
        return vo;
    }

    private void fillCommon(AdminMemeListItemVO vo, Meme meme, Map<Long, String> nicknameMap) {
        vo.setId(meme.getId() == null ? null : meme.getId().longValue());
        vo.setName(meme.getName());
        vo.setIntroduction(meme.getIntroduction());
        vo.setImage(ossUrlHelper.toPublicUrl(meme.getImage()));
        vo.setStatus(meme.getStatus());
        vo.setStatusDesc(MemeVisibilitySupport.statusDesc(meme.getStatus()));
        vo.setUserId(meme.getUserId());
        vo.setAuthorNickname(meme.getUserId() == null ? null : nicknameMap.get(meme.getUserId()));
        vo.setPageViews(meme.getPageViews());
        vo.setLikes(meme.getLikes());
        vo.setComments(meme.getComments());
        vo.setReleaseTime(meme.getReleaseTime());
        vo.setUpdateTime(meme.getUpdateTime());
        vo.setOfflineReason(meme.getOfflineReason());
        vo.setAppealRejectCount(meme.getAppealRejectCount());
    }

    private void fillCommon(AdminMemeDetailVO vo, Meme meme, Map<Long, String> nicknameMap) {
        vo.setId(meme.getId() == null ? null : meme.getId().longValue());
        vo.setName(meme.getName());
        vo.setIntroduction(meme.getIntroduction());
        vo.setImage(ossUrlHelper.toPublicUrl(meme.getImage()));
        vo.setStatus(meme.getStatus());
        vo.setStatusDesc(MemeVisibilitySupport.statusDesc(meme.getStatus()));
        vo.setUserId(meme.getUserId());
        vo.setAuthorNickname(meme.getUserId() == null ? null : nicknameMap.get(meme.getUserId()));
        vo.setPageViews(meme.getPageViews());
        vo.setLikes(meme.getLikes());
        vo.setComments(meme.getComments());
        vo.setReleaseTime(meme.getReleaseTime());
        vo.setUpdateTime(meme.getUpdateTime());
        vo.setOfflineReason(meme.getOfflineReason());
        vo.setAppealRejectCount(meme.getAppealRejectCount());
    }

    private AdminMemeActionVO toAction(Meme meme) {
        AdminMemeActionVO vo = new AdminMemeActionVO();
        vo.setMemeId(meme.getId() == null ? null : meme.getId().longValue());
        vo.setStatus(meme.getStatus());
        vo.setStatusDesc(MemeVisibilitySupport.statusDesc(meme.getStatus()));
        return vo;
    }
}
