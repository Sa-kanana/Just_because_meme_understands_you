package com.sakana.just_because_meme_understands_you.modules.user.application.follow;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserTagFollowMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagFollowService {
    @Resource private MemeTagMapper tagMapper;
    @Resource private UserTagFollowMapper followMapper;

    public List<MemeTagVO> list(Long userId) {
        List<MemeTag> tags = followMapper.selectFollowedTags(userId);
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream().map(tag -> {
            MemeTagVO vo = new MemeTagVO();
            vo.setId(tag.getId());
            vo.setName(tag.getName());
            vo.setRelatedQuantity(parseRelatedQuantity(tag.getRelatedQuantity()));
            return vo;
        }).toList();
    }

    private Integer parseRelatedQuantity(String relatedQuantity) {
        if (relatedQuantity == null || relatedQuantity.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(relatedQuantity);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public void follow(Long userId, Integer tagId) {
        if (tagId == null || tagId <= 0 || tagMapper.selectById(tagId) == null) {
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
