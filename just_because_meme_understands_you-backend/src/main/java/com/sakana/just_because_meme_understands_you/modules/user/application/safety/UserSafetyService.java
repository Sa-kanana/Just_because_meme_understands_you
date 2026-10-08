package com.sakana.just_because_meme_understands_you.modules.user.application.safety;

import com.sakana.just_because_meme_understands_you.api.user.vo.UserSuppressionItemVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.UserSuppressionStatusVO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserSuppression;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserMapper;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserSuppressionMapper;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class UserSafetyService {
    public static final String MUTE = "MUTE";
    public static final String BLOCK = "BLOCK";
    private static final Set<String> TYPES = Set.of(MUTE, BLOCK);
    private static final int USER_ACTIVE = 1;

    @Resource private UserSuppressionMapper suppressionMapper;
    @Resource private UserMapper userMapper;

    @Transactional(rollbackFor = Exception.class)
    public UserSuppressionStatusVO apply(Long ownerId, Long targetId, String type) {
        validate(ownerId, targetId, type);
        User target = userMapper.selectById(targetId);
        if (target == null || !Integer.valueOf(USER_ACTIVE).equals(target.getStatus())) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在或不可操作");
        }
        if (suppressionMapper.countRelation(ownerId, targetId, type) == 0) {
            UserSuppression relation = new UserSuppression();
            relation.setOwnerUserId(ownerId);
            relation.setTargetUserId(targetId);
            relation.setType(type);
            relation.setCreatedAt(LocalDateTime.now());
            try {
                suppressionMapper.insert(relation);
            } catch (DuplicateKeyException ignored) {
                // 并发重复操作按幂等成功处理。
            }
        }
        return status(ownerId, targetId);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserSuppressionStatusVO remove(Long ownerId, Long targetId, String type) {
        validate(ownerId, targetId, type);
        suppressionMapper.deleteOwned(ownerId, targetId, type);
        return status(ownerId, targetId);
    }

    public List<UserSuppressionItemVO> list(Long ownerId) {
        requireId(ownerId, "当前用户");
        List<UserSuppressionItemVO> items = new ArrayList<>();
        List<UserSuppression> outgoing = suppressionMapper.selectOwned(ownerId);
        if (outgoing != null) outgoing.forEach(relation -> appendListItem(items, relation,
                relation.getTargetUserId(), "OUTGOING"));
        List<UserSuppression> incomingBlocks = suppressionMapper.selectBlocksTargeting(ownerId);
        if (incomingBlocks != null) incomingBlocks.forEach(relation -> appendListItem(items, relation,
                relation.getOwnerUserId(), "INCOMING"));
        return items;
    }

    public UserSuppressionStatusVO status(Long ownerId, Long targetId) {
        requireId(ownerId, "当前用户");
        requireId(targetId, "目标用户");
        UserSuppressionStatusVO status = new UserSuppressionStatusVO();
        status.setTargetUserId(targetId);
        status.setMuted(suppressionMapper.countRelation(ownerId, targetId, MUTE) > 0);
        status.setBlockedByMe(suppressionMapper.countRelation(ownerId, targetId, BLOCK) > 0);
        status.setBlockedByTarget(suppressionMapper.countRelation(targetId, ownerId, BLOCK) > 0);
        status.setBlocked(status.isBlockedByMe() || status.isBlockedByTarget());
        return status;
    }

    /** 返回的隐藏状态只依赖当前浏览者主动设置的 MUTE，BLOCK 对双方生效。 */
    public boolean isHidden(Long viewerId, Long authorId) {
        if (viewerId == null || authorId == null || viewerId <= 0 || authorId <= 0 || viewerId.equals(authorId)) {
            return false;
        }
        return suppressionMapper.countRelation(viewerId, authorId, MUTE) > 0
                || suppressionMapper.existsBlockBetween(viewerId, authorId) > 0;
    }

    /** 在新增互动时检查双方是否存在屏蔽关系。 */
    public void assertInteractionAllowed(Long actorId, Long otherUserId) {
        if (actorId == null || actorId <= 0 || otherUserId == null || otherUserId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "用户参数不合法");
        }
        if (!actorId.equals(otherUserId) && suppressionMapper.existsBlockBetween(actorId, otherUserId) > 0) {
            throw new BizException(Result.CODE_FORBIDDEN, "你与该用户存在屏蔽关系，无法进行此操作");
        }
    }

    private void validate(Long ownerId, Long targetId, String type) {
        requireId(ownerId, "当前用户");
        requireId(targetId, "目标用户");
        if (ownerId.equals(targetId)) throw new BizException(Result.CODE_BAD_REQUEST, "不能屏蔽或静音自己");
        if (type == null || !TYPES.contains(type)) throw new BizException(Result.CODE_BAD_REQUEST, "安全关系类型不合法");
    }

    private void appendListItem(List<UserSuppressionItemVO> items, UserSuppression relation,
                                Long otherUserId, String direction) {
        User other = userMapper.selectById(otherUserId);
        if (other == null) return;
        UserSuppressionItemVO item = new UserSuppressionItemVO();
        item.setTargetUserId(other.getId());
        item.setType(relation.getType());
        item.setDirection(direction);
        item.setNickname(other.getNickname());
        item.setAvatar(other.getAvatar());
        item.setCreatedAt(relation.getCreatedAt());
        items.add(item);
    }

    private void requireId(Long id, String field) {
        if (id == null || id <= 0) throw new BizException(Result.CODE_BAD_REQUEST, field + "参数不合法");
    }
}
