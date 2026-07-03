package com.sakana.just_because_meme_understands_you.service.comment.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.dto.MemeCommentCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.entity.MemeComment;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.mapper.MemeCommentMapper;
import com.sakana.just_because_meme_understands_you.service.comment.IMemeCommentService;
import com.sakana.just_because_meme_understands_you.service.comment.MemeCommentAsyncHandler;
import com.sakana.just_because_meme_understands_you.service.comment.SensitiveWordFilterService;
import com.sakana.just_because_meme_understands_you.service.comment.support.MemeCommentSupport;
import com.sakana.just_because_meme_understands_you.service.meme.IMemeService;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentCreateResponseVO;
import com.sakana.just_because_meme_understands_you.vo.MemeCommentPageVO;
import com.sakana.just_because_meme_understands_you.vo.MemeReplyCommentVO;
import com.sakana.just_because_meme_understands_you.vo.MemeRootCommentVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class MemeCommentServiceImpl implements IMemeCommentService {

    private static final String SORT_HOT = "hot";

    @Resource
    private MemeCommentMapper memeCommentMapper;

    @Resource
    private IMemeService memeService;

    @Resource
    private SensitiveWordFilterService sensitiveWordFilterService;

    @Resource
    private MemeCommentAsyncHandler memeCommentAsyncHandler;

    @Resource
    private MemeCommentSupport commentSupport;

    @Override
    public MemeCommentPageVO pageRootComments(Long memeId, Integer page, Integer size, String sortType) {
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不合法");
        }
        Meme meme = memeService.getById(memeId);
        if (meme == null || meme.getStatus() == null || meme.getStatus() != 1) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或不可查看评论");
        }

        int pageNo = commentSupport.normalizePage(page);
        int pageSize = commentSupport.normalizeSize(size);
        Page<MemeComment> mpPage = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<MemeComment> wrapper = new LambdaQueryWrapper<MemeComment>()
                .eq(MemeComment::getMemeId, memeId)
                .eq(MemeComment::getRootId, 0L)
                .eq(MemeComment::getIsDeleted, MemeCommentSupport.NOT_DELETED);
        if (SORT_HOT.equalsIgnoreCase(StringUtils.hasText(sortType) ? sortType.trim() : "")) {
            wrapper.orderByDesc(MemeComment::getLikes).orderByDesc(MemeComment::getCreateTime);
        } else {
            wrapper.orderByDesc(MemeComment::getCreateTime);
        }

        IPage<MemeComment> result = memeCommentMapper.selectPage(mpPage, wrapper);
        List<MemeComment> records = result.getRecords();
        Map<Long, User> userMap = commentSupport.loadUserMap(records.stream().map(MemeComment::getUserId).toList());
        Map<Long, List<String>> imageMap = commentSupport.loadImageMap(records.stream().map(MemeComment::getId).toList());

        List<MemeRootCommentVO> list = new ArrayList<>();
        if (records != null) {
            for (MemeComment comment : records) {
                list.add(commentSupport.toRootVO(comment, userMap, imageMap));
            }
        }

        MemeCommentPageVO pageVO = new MemeCommentPageVO();
        pageVO.setList(list);
        pageVO.setTotal(result.getTotal());
        pageVO.setHasMore(result.getCurrent() * result.getSize() < result.getTotal());
        return pageVO;
    }

    @Override
    public List<MemeReplyCommentVO> pageReplies(Long rootId, Integer page, Integer size) {
        if (rootId == null || rootId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "rootId 不合法");
        }
        MemeComment root = memeCommentMapper.selectById(rootId);
        if (root == null || !Objects.equals(root.getRootId(), 0L)
                || !Objects.equals(root.getIsDeleted(), MemeCommentSupport.NOT_DELETED)) {
            throw new BizException(Result.CODE_NOT_FOUND, "根评论不存在");
        }

        int pageNo = commentSupport.normalizePage(page);
        int pageSize = commentSupport.normalizeSize(size);
        Page<MemeComment> mpPage = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<MemeComment> wrapper = new LambdaQueryWrapper<MemeComment>()
                .eq(MemeComment::getRootId, rootId)
                .ne(MemeComment::getId, rootId)
                .eq(MemeComment::getIsDeleted, MemeCommentSupport.NOT_DELETED)
                .orderByAsc(MemeComment::getCreateTime);
        IPage<MemeComment> result = memeCommentMapper.selectPage(mpPage, wrapper);
        List<MemeComment> records = result.getRecords();
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, User> userMap = commentSupport.loadUserMap(records.stream().map(MemeComment::getUserId).toList());
        Map<Long, MemeComment> parentMap = commentSupport.loadCommentMap(records.stream().map(MemeComment::getParentId).toList());
        Map<Long, List<String>> imageMap = commentSupport.loadImageMap(records.stream().map(MemeComment::getId).toList());

        List<MemeReplyCommentVO> list = new ArrayList<>(records.size());
        for (MemeComment comment : records) {
            list.add(commentSupport.toReplyVO(comment, userMap, parentMap, imageMap));
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemeCommentCreateResponseVO createComment(Long userId, MemeCommentCreateRequestDTO request) {
        commentSupport.validateUser(userId);
        commentSupport.validateCreateRequest(request);

        Long memeId = request.getMemeId();
        if (memeId == null || memeId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "memeId 不合法");
        }
        Meme meme = memeService.getById(memeId);
        if (meme == null || meme.getStatus() == null || meme.getStatus() != 1) {
            throw new BizException(Result.CODE_NOT_FOUND, "梗不存在或不可评论");
        }

        long rootId = commentSupport.parseCommentRefId(request.getRootId(), "rootId");
        long parentId = commentSupport.parseCommentRefId(request.getParentId(), "parentId");
        boolean isRootComment = rootId == 0L && parentId == 0L;

        String filteredContent = sensitiveWordFilterService.filterForInsert(request.getContent().trim());
        LocalDateTime now = LocalDateTime.now();
        MemeComment comment = new MemeComment();
        comment.setMemeId(memeId);
        comment.setUserId(userId);
        comment.setContent(filteredContent);
        comment.setReplyCount(0);
        comment.setLikes(0);
        comment.setIsDeleted(MemeCommentSupport.NOT_DELETED);
        comment.setCreateTime(now);
        comment.setUpdateTime(now);

        if (isRootComment) {
            comment.setRootId(0L);
            comment.setParentId(0L);
        } else {
            if (rootId <= 0) {
                throw new BizException(Result.CODE_BAD_REQUEST, "rootId 不合法");
            }
            MemeComment root = memeCommentMapper.selectById(rootId);
            if (root == null || !Objects.equals(root.getRootId(), 0L)
                    || !Objects.equals(root.getIsDeleted(), MemeCommentSupport.NOT_DELETED)) {
                throw new BizException(Result.CODE_NOT_FOUND, "根评论不存在");
            }
            if (!Objects.equals(root.getMemeId(), memeId)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "评论与梗不匹配");
            }
            long resolvedParentId = parentId > 0 ? parentId : rootId;
            MemeComment parent = memeCommentMapper.selectById(resolvedParentId);
            if (!commentSupport.isValidReplyParent(parent, rootId) || !Objects.equals(parent.getMemeId(), memeId)) {
                throw new BizException(Result.CODE_BAD_REQUEST, "父评论不存在");
            }
            comment.setRootId(rootId);
            comment.setParentId(resolvedParentId);
        }

        memeCommentMapper.insert(comment);
        commentSupport.saveImages(comment.getId(), request.getImageUrls());

        if (!isRootComment) {
            memeCommentMapper.update(null, new LambdaUpdateWrapper<MemeComment>()
                    .eq(MemeComment::getId, rootId)
                    .setSql("reply_count = reply_count + 1")
                    .set(MemeComment::getUpdateTime, now));
        }

        scheduleAfterCommentCommitted(memeId, userId);
        return commentSupport.toCreateResponseVO(comment);
    }

    private void scheduleAfterCommentCommitted(Long memeId, Long userId) {
        Runnable task = () -> memeCommentAsyncHandler.afterCommentCreated(memeId, userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
