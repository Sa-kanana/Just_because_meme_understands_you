package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.auth.application.UserSessionRevoker;
import com.sakana.just_because_meme_understands_you.modules.user.application.UserRoleService;
import com.sakana.just_because_meme_understands_you.modules.user.application.profile.IUserProfileService;
import com.sakana.just_because_meme_understands_you.modules.user.domain.UserDisplayPolicy;
import com.sakana.just_because_meme_understands_you.modules.user.domain.UserStatusConstants;
import com.sakana.just_because_meme_understands_you.common.pagination.PageParamNormalizer;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserMapper;
import com.sakana.just_because_meme_understands_you.modules.admin.application.IAdminUserService;
import com.sakana.just_because_meme_understands_you.modules.admin.application.assembler.AdminViewMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminUserListItemVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminUserServiceImpl implements IAdminUserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private AdminViewMapper adminViewMapper;

    @Resource
    private OssUrlHelper ossUrlHelper;

    @Resource
    private UserRoleService userRoleService;

    @Resource
    private UserSessionRevoker userSessionRevoker;

    @Resource
    private IUserProfileService userProfileService;

    @Override
    public PageVO<AdminUserListItemVO> page(Long operatorId, Integer page, Integer size, String keyword,
                                             Integer status, String role, boolean deletedOnly) {
        if (deletedOnly && !isSuperAdmin(operatorId)) {
            throw new BizException(Result.CODE_FORBIDDEN, "只有超级管理员可以查看已注销用户");
        }
        int pageNo = PageParamNormalizer.normalizePage(page);
        int pageSize = PageParamNormalizer.normalizeSize(size);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (deletedOnly) {
            wrapper.isNotNull(User::getDeletedAt);
        } else {
            wrapper.isNull(User::getDeletedAt);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        if (StringUtils.hasText(role)) {
            String roleCode = normalizeRole(role);
            List<Long> userIds = userRoleService.findUserIdsByRole(roleCode);
            if (userIds == null || userIds.isEmpty()) {
                return emptyPage(pageNo, pageSize);
            }
            wrapper.in(User::getId, userIds);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getNickname, kw).or().like(User::getSignature, kw));
        }
        wrapper.orderByDesc(User::getCreateTime).orderByDesc(User::getId);

        Page<User> mpPage = userMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<User> records = mpPage.getRecords() != null ? mpPage.getRecords() : Collections.emptyList();
        Map<Long, List<String>> rolesByUserId = userRoleService.getRoleCodesByUserIds(
                records.stream().map(User::getId).filter(Objects::nonNull).toList());

        PageVO<AdminUserListItemVO> vo = new PageVO<>();
        vo.setList(records.stream().map(user -> toVO(user,
                rolesByUserId.getOrDefault(user.getId(), List.of(UserRoleService.USER)))).collect(Collectors.toList()));
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
        ensureNotDeleted(user);
        if (Objects.equals(operatorId, targetUserId) && Objects.equals(status, UserStatusConstants.DISABLED)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "不能禁用当前登录账号");
        }
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        boolean targetIsSuperAdmin = userRoleService.hasAnyRole(targetUserId, UserRoleService.SUPER_ADMIN);
        if (targetIsSuperAdmin) {
            if (!userRoleService.hasAnyRole(operatorId, UserRoleService.SUPER_ADMIN)) {
                throw new BizException(Result.CODE_FORBIDDEN, "只有超级管理员可以管理超级管理员账号");
            }
            if (Objects.equals(status, UserStatusConstants.DISABLED)
                    && userRoleService.lockUserIdsByRole(UserRoleService.SUPER_ADMIN).size() <= 1) {
                throw new BizException(Result.CODE_BAD_REQUEST, "不能禁用最后一个超级管理员");
            }
        }
        userMapper.updateById(user);
        if (Objects.equals(status, UserStatusConstants.DISABLED)) {
            userSessionRevoker.revokeAllByUserId(String.valueOf(targetUserId));
        }
        return toVO(user, userRoleService.getRoleCodes(targetUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserListItemVO updateRole(Long operatorId, Long targetUserId, String role) {
        if (Objects.equals(operatorId, targetUserId)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "不能修改当前登录账号的角色");
        }
        String normalized;
        try {
            normalized = UserRoleService.normalizeRoleCode(role);
        } catch (IllegalArgumentException exception) {
            throw new BizException(Result.CODE_BAD_REQUEST, "role 仅支持 USER、ADMIN 或 SUPER_ADMIN");
        }
        boolean operatorIsSuperAdmin = userRoleService.hasAnyRole(operatorId, UserRoleService.SUPER_ADMIN);
        if (UserRoleService.SUPER_ADMIN.equals(normalized) && !operatorIsSuperAdmin) {
            throw new BizException(Result.CODE_FORBIDDEN, "只有超级管理员可以授予 SUPER_ADMIN 角色");
        }
        User user = requireUser(targetUserId);
        ensureNotDeleted(user);
        List<String> currentRoles = userRoleService.getRoleCodes(targetUserId);
        boolean targetIsSuperAdmin = currentRoles.contains(UserRoleService.SUPER_ADMIN);
        if (targetIsSuperAdmin && !operatorIsSuperAdmin) {
            throw new BizException(Result.CODE_FORBIDDEN, "只有超级管理员可以修改超级管理员账号");
        }
        if (targetIsSuperAdmin && !UserRoleService.SUPER_ADMIN.equals(normalized)
                && userRoleService.lockUserIdsByRole(UserRoleService.SUPER_ADMIN).size() <= 1) {
            throw new BizException(Result.CODE_BAD_REQUEST, "不能移除最后一个超级管理员");
        }
        List<String> replacementRoles = rolesFor(normalized);
        if (new java.util.LinkedHashSet<>(currentRoles).equals(new java.util.LinkedHashSet<>(replacementRoles))) {
            return toVO(user, currentRoles);
        }
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
        userRoleService.replaceRoles(targetUserId, replacementRoles);
        userSessionRevoker.revokeAllByUserId(String.valueOf(targetUserId));
        return toVO(user, replacementRoles);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserListItemVO delete(Long operatorId, Long targetUserId) {
        if (Objects.equals(operatorId, targetUserId)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "不能删除当前登录账号");
        }
        User user = requireUser(targetUserId);
        ensureNotDeleted(user);

        boolean operatorIsSuperAdmin = isSuperAdmin(operatorId);
        List<String> targetRoles = userRoleService.getRoleCodes(targetUserId);
        boolean targetIsAdmin = targetRoles.contains(UserRoleService.ADMIN);
        boolean targetIsSuperAdmin = targetRoles.contains(UserRoleService.SUPER_ADMIN);
        if (!operatorIsSuperAdmin && (targetIsAdmin || targetIsSuperAdmin)) {
            throw new BizException(Result.CODE_FORBIDDEN, "普通管理员不能删除管理员或超级管理员");
        }
        if (targetIsSuperAdmin
                && userRoleService.lockNotDeletedUserIdsByRole(UserRoleService.SUPER_ADMIN).size() <= 1) {
            throw new BizException(Result.CODE_BAD_REQUEST, "不能删除最后一个超级管理员");
        }

        LocalDateTime now = LocalDateTime.now();
        user.setDeletedAt(now);
        user.setUpdateTime(now);
        userMapper.updateById(user);
        userSessionRevoker.revokeAllByUserId(String.valueOf(targetUserId));
        userProfileService.evictUserCache(targetUserId);
        return toVO(user, targetRoles);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserListItemVO restore(Long operatorId, Long targetUserId) {
        if (!isSuperAdmin(operatorId)) {
            throw new BizException(Result.CODE_FORBIDDEN, "只有超级管理员可以恢复已注销用户");
        }
        User user = requireUser(targetUserId);
        if (!UserDisplayPolicy.isDeleted(user)) {
            throw new BizException(Result.CODE_BAD_REQUEST, "该用户未注销，无需恢复");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = userMapper.update(null, new UpdateWrapper<User>()
                .set("deleted_at", null)
                .set("update_time", now)
                .eq("id", targetUserId)
                .isNotNull("deleted_at"));
        if (updated == 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "该账号状态已变化，请刷新后重试");
        }
        user.setDeletedAt(null);
        user.setUpdateTime(now);
        userProfileService.evictUserCache(targetUserId);
        return toVO(user, userRoleService.getRoleCodes(targetUserId));
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

    private void ensureNotDeleted(User user) {
        if (UserDisplayPolicy.isDeleted(user)) {
            throw new BizException(Result.CODE_NOT_FOUND, "用户不存在");
        }
    }

    private boolean isSuperAdmin(Long userId) {
        return userId != null && userRoleService.hasAnyRole(userId, UserRoleService.SUPER_ADMIN);
    }

    private AdminUserListItemVO toVO(User user, List<String> roleCodes) {
        AdminUserListItemVO vo = adminViewMapper.toAdminUserListItemVO(user);
        vo.setAvatar(ossUrlHelper.toPublicUrl(user.getAvatar()));
        vo.setRole(toAuthority(roleCodes));
        return vo;
    }

    private PageVO<AdminUserListItemVO> emptyPage(int page, int size) {
        PageVO<AdminUserListItemVO> pageVO = new PageVO<>();
        pageVO.setList(List.of());
        pageVO.setPage(page);
        pageVO.setSize(size);
        pageVO.setTotal(0L);
        pageVO.setHasMore(false);
        return pageVO;
    }

    private String normalizeRole(String role) {
        try {
            return UserRoleService.normalizeRoleCode(role);
        } catch (IllegalArgumentException exception) {
            throw new BizException(Result.CODE_BAD_REQUEST, "role 仅支持 USER、ADMIN 或 SUPER_ADMIN");
        }
    }

    private List<String> rolesFor(String roleCode) {
        return switch (roleCode) {
            case UserRoleService.SUPER_ADMIN -> List.of(UserRoleService.USER, UserRoleService.ADMIN, UserRoleService.SUPER_ADMIN);
            case UserRoleService.ADMIN -> List.of(UserRoleService.USER, UserRoleService.ADMIN);
            default -> List.of(UserRoleService.USER);
        };
    }

    private String toAuthority(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return "ROLE_USER";
        }
        return "ROLE_" + roleCodes.get(0);
    }
}
