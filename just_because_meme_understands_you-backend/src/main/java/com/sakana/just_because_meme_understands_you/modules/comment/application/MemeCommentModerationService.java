package com.sakana.just_because_meme_understands_you.modules.comment.application;

import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.comment.domain.entity.MemeComment;
import com.sakana.just_because_meme_understands_you.modules.comment.contract.CommentModerationPort;
import com.sakana.just_because_meme_understands_you.modules.comment.infrastructure.persistence.mapper.MemeCommentMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** 评论审核及可见性调整应用服务。 */
@Service
public class MemeCommentModerationService implements CommentModerationPort {

    private static final int NOT_DELETED = 0;

    @Resource
    private MemeCommentMapper memeCommentMapper;

    @Resource
    private MemeCatalogPort memeCatalogPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int hideComment(long commentId) {
        if (commentId <= 0) {
            return 0;
        }
        MemeComment comment = memeCommentMapper.selectById(commentId);
        if (comment == null || !Objects.equals(comment.getIsDeleted(), NOT_DELETED)) {
            return 0;
        }

        Long rootId = comment.getRootId();
        boolean isRoot = rootId == null || rootId <= 0;
        int hiddenCount = memeCommentMapper.hideById(commentId);
        if (hiddenCount != 1) {
            return 0;
        }

        if (isRoot) {
            hiddenCount += memeCommentMapper.hideRepliesByRootId(commentId);
        } else {
            memeCommentMapper.decrementReplyCount(rootId);
        }

        Long memeId = comment.getMemeId();
        if (memeId == null || !memeCatalogPort.decrementCommentCount(memeId, hiddenCount)) {
            throw new BizException(Result.CODE_NOT_FOUND, "评论所属梗不存在，无法更新评论数");
        }
        return hiddenCount;
    }
}
