package com.sakana.just_because_meme_understands_you.service.ai.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.constant.DataStatusConstants;
import com.sakana.just_because_meme_understands_you.common.support.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.entity.AiChatMessage;
import com.sakana.just_because_meme_understands_you.entity.AiChatSession;
import com.sakana.just_because_meme_understands_you.mapper.AiChatMessageMapper;
import com.sakana.just_because_meme_understands_you.mapper.AiChatSessionMapper;
import com.sakana.just_because_meme_understands_you.service.ai.IAiChatSessionService;
import com.sakana.just_because_meme_understands_you.vo.AiChatMessagePageVO;
import com.sakana.just_because_meme_understands_you.vo.AiChatMessageVO;
import com.sakana.just_because_meme_understands_you.vo.AiChatSessionPageVO;
import com.sakana.just_because_meme_understands_you.vo.AiChatSessionVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiChatSessionServiceImpl implements IAiChatSessionService {

    @Resource
    private AiChatSessionMapper sessionMapper;

    @Resource
    private AiChatMessageMapper messageMapper;

    /**
     * 懒加载用户会话
     * @param userId
     * @param page
     * @param size
     * @return
     */
    @Override
    public AiChatSessionPageVO pageSessions(Long userId, Integer page, Integer size) {
        requireUserId(userId);
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        Page<AiChatSession> result = sessionMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<AiChatSession>()
                        .eq(AiChatSession::getUserId, userId)
                        .eq(AiChatSession::getIsDeleted, DataStatusConstants.NOT_DELETED)
                        .orderByDesc(AiChatSession::getUpdateTime)
                        .orderByDesc(AiChatSession::getId));

        List<AiChatSessionVO> list = new ArrayList<>();
        for (AiChatSession row : result.getRecords()) {
            list.add(toSessionVO(row));
        }

        AiChatSessionPageVO vo = new AiChatSessionPageVO();
        vo.setList(list);
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setTotal(result.getTotal());
        vo.setHasMore((long) pageNo * pageSize < result.getTotal());
        return vo;
    }

    /**
     * 懒加载会话消息
     * @param userId
     * @param sessionId
     * @param page
     * @param size
     * @return
     */
    @Override
    public AiChatMessagePageVO pageMessages(Long userId, Long sessionId, Integer page, Integer size) {
        requireUserId(userId);
        requireOwnedSession(userId, sessionId);

        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        Page<AiChatMessage> result = messageMapper.selectPage(
                new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getSessionId, sessionId)
                        .orderByAsc(AiChatMessage::getCreateTime)
                        .orderByAsc(AiChatMessage::getId));

        List<AiChatMessageVO> list = new ArrayList<>();
        for (AiChatMessage row : result.getRecords()) {
            list.add(toMessageVO(row));
        }

        AiChatMessagePageVO vo = new AiChatMessagePageVO();
        vo.setList(list);
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setTotal(result.getTotal());
        vo.setHasMore((long) pageNo * pageSize < result.getTotal());
        return vo;
    }

    /**
     * 软删除会话
     * @param userId
     * @param sessionId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void softDeleteSession(Long userId, Long sessionId) {
        // ① 基础权限校验：确保 userId 有效
        requireUserId(userId);
        // ② 归属权校验：确保该会话确实属于当前用户
        requireOwnedSession(userId, sessionId);

        int updated = sessionMapper.update(null, new LambdaUpdateWrapper<AiChatSession>()
                // 条件：ID 匹配 + 用户 ID 匹配 + 当前状态为未删除
                .eq(AiChatSession::getId, sessionId)
                .eq(AiChatSession::getUserId, userId)
                .eq(AiChatSession::getIsDeleted, DataStatusConstants.NOT_DELETED)
                // 设置：标记为已删除 + 更新修改时间
                .set(AiChatSession::getIsDeleted, DataStatusConstants.DELETED)
                .set(AiChatSession::getUpdateTime, LocalDateTime.now()));
        if (updated <= 0) {
            throw new BizException(Result.CODE_NOT_FOUND, "会话不存在或已删除");
        }
    }

    /**
     * 获取自己的会话
     * @param userId
     * @param sessionId
     * @return
     */
    private AiChatSession requireOwnedSession(Long userId, Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "sessionId 不合法");
        }
        AiChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<AiChatSession>()
                .eq(AiChatSession::getId, sessionId)
                .eq(AiChatSession::getUserId, userId)
                .eq(AiChatSession::getIsDeleted, DataStatusConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (session == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "会话不存在或已删除");
        }
        return session;
    }

    /**
     * 校验用户 ID 是否有效
     * @param userId
     */
    private void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_UNAUTHORIZED, "未登录或登录已过期");
        }
    }

    private AiChatSessionVO toSessionVO(AiChatSession row) {
        AiChatSessionVO vo = new AiChatSessionVO();
        vo.setId(row.getId());
        vo.setTitle(row.getTitle());
        vo.setCreateTime(row.getCreateTime());
        vo.setUpdateTime(row.getUpdateTime());
        return vo;
    }

    private AiChatMessageVO toMessageVO(AiChatMessage row) {
        AiChatMessageVO vo = new AiChatMessageVO();
        vo.setId(row.getId());
        vo.setSessionId(row.getSessionId());
        vo.setRole(row.getRole());
        vo.setContent(row.getContent());
        vo.setRequestId(row.getRequestId());
        vo.setTokenEstimate(row.getTokenEstimate());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}
