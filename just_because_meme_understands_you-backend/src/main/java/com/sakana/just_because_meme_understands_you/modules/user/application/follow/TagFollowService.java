package com.sakana.just_because_meme_understands_you.modules.user.application.follow;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserTagFollowMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagFollowService {
    @Resource private MemeCatalogPort memeCatalogPort;
    @Resource private UserTagFollowMapper followMapper;

    public List<MemeTagVO> list(Long userId) {
        List<Long> tagIds = followMapper.selectFollowedTagIds(userId);
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return memeCatalogPort.findTagsByIds(tagIds).stream().map(tag -> {
            MemeTagVO vo = new MemeTagVO();
            vo.setId(tag.id() == null ? null : tag.id().intValue());
            vo.setName(tag.name());
            vo.setRelatedQuantity(tag.relatedQuantity());
            return vo;
        }).toList();
    }

    public void follow(Long userId, Integer tagId) {
        if (tagId == null || tagId <= 0 || memeCatalogPort.findTagById(tagId) == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "标签不存在");
        }
        followMapper.insertIfAbsent(userId, tagId);
    }

    public void unfollow(Long userId, Integer tagId) {
        if (tagId == null || tagId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "标签 ID 无效");
        }
        followMapper.deleteFollow(userId, tagId);
    }
}
