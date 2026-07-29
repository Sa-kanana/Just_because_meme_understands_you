package com.sakana.just_because_meme_understands_you.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.BizException;
import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.constant.AuthConstants;
import com.sakana.just_because_meme_understands_you.common.constant.UserStatusConstants;
import com.sakana.just_because_meme_understands_you.common.support.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.mapper.UserMapper;
import com.sakana.just_because_meme_understands_you.service.admin.IAdminUserService;
import com.sakana.just_because_meme_understands_you.service.oss.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.vo.PageVO;
import com.sakana.just_because_meme_understands_you.vo.admin.AdminUserListItemVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminUserServiceImpl implements IAdminUserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Override
    public PageVO<AdminUserListItemVO> page(Integer page, Integer size, String keyword, Integer status, String role) {
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        if (StringUtils.hasText(role)) {
            wrapper.eq(User::getRole, role.trim());
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getNickname, kw).or().like(User::getSignature, kw));
        }
        wrapper.orderByDesc(User::getCreateTime).orderByDesc(User::getId);

        Page<User> mpPage = userMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<User> records = mpPage.getRecords() != null ? mpPage.getRecords() : Collections.emptyList();

        PageVO<AdminUserListItemVO> vo = new PageVO<>();
        vo.setList(records.stream().map(this::toVO).collect(Collectors.toList()));
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setTotal(mpPage.getTotal());
        vo.setHasMore(mpPage.getCurrent() * mpPage.getSize() < mpPage.getTotal());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserListItemVO updateStatus(Long operatorId, Long targetUserId, Integer status) {
        if (!Objects.equals(status, UserStatusConstants.DISABLED)
                && !Objects.equals(status, UserStatusConstants.ACTIVE)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "status 仅支持 0 或 1");
        }
        User user = requireUser(targetUserId);
        if (Objects.equals(operatorId, targetUserId) && Objects.equals(status, UserStatusConstants.DISABLED)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "不能禁用当前登录账号");
        }
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return toVO(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserListItemVO updateRole(Long operatorId, Long targetUserId, String role) {
        String normalized = role == null ? "" : role.trim();
        if (!AuthConstants.ROLE_USER.equals(normalized) && !AuthConstants.ROLE_ADMIN.equals(normalized)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "role 仅支持 ROLE_USER 或 ROLE_ADMIN");
        }
        if (Objects.equals(operatorId, targetUserId)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "不能修改当前登录账号的角色");
        }
        User user = requireUser(targetUserId);
        if (AuthConstants.ROLE_ADMIN.equals(user.getRole())
                && AuthConstants.ROLE_USER.equals(normalized)
                && countAdmins() <= 1) {
            throw new BizException(Result.CODE_BAD_REQUEST, "不能取消最后一个管理员");
        }
        user.setRole(normalized);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        return toVO(user);
    }

    private long countAdmins() {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getRole, AuthConstants.ROLE_ADMIN));
    }

    private User requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "userId 不合法");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private AdminUserListItemVO toVO(User user) {
        AdminUserListItemVO vo = new AdminUserListItemVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(ossUrlHelper.toPublicUrl(user.getAvatar()));
        vo.setSignature(user.getSignature());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setCreateTime(user.getCreateTime());
        vo.setUpdateTime(user.getUpdateTime());
        return vo;
    }
}
