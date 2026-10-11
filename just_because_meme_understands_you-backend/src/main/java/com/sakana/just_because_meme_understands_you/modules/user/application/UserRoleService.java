package com.sakana.just_because_meme_understands_you.modules.user.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.Role;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserRole;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.RoleMapper;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserRoleMapper;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.projection.UserRoleAssignment;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 用户角色查询与维护服务。 */
@Service
public class UserRoleService {

    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    private static final List<String> ROLE_PRIORITY = List.of(SUPER_ADMIN, ADMIN, USER);

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private UserRoleMapper userRoleMapper;

    /** 获取用户角色，按权限从高到低排序。 */
    public List<String> getRoleCodes(Long userId) {
        List<String> codes = userRoleMapper.selectRoleCodesByUserId(userId);
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return codes.stream()
                .map(UserRoleService::normalizeRoleCode)
                .filter(ROLE_PRIORITY::contains)
                .distinct()
                .sorted(Comparator.comparingInt(ROLE_PRIORITY::indexOf))
                .toList();
    }

    /** 判断用户是否持有任意指定角色，接受 USER 与 ROLE_USER 两种写法。 */
    public boolean hasAnyRole(Long userId, String... roleCodes) {
        Set<String> expected = new LinkedHashSet<>();
        if (roleCodes != null) {
            for (String roleCode : roleCodes) {
                expected.add(normalizeRoleCode(roleCode));
            }
        }
        return getRoleCodes(userId).stream().anyMatch(expected::contains);
    }

    /** 将用户当前角色整体替换为给定角色集合。 */
    @Transactional(rollbackFor = Exception.class)
    public void replaceRoles(Long userId, Collection<String> roleCodes) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId 必须为正数");
        }
        Set<String> normalizedCodes = normalizeRoleCodes(roleCodes);
        if (normalizedCodes.isEmpty()) {
            throw new IllegalArgumentException("用户至少需要一个角色");
        }

        List<Role> roles = new ArrayList<>(normalizedCodes.size());
        for (String code : normalizedCodes) {
            Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, code));
            if (role == null) {
                throw new IllegalStateException("角色未初始化: " + code);
            }
            roles.add(role);
        }

        userRoleMapper.deleteByUserId(userId);
        LocalDateTime now = LocalDateTime.now();
        for (Role role : roles) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(role.getId());
            userRole.setCreateTime(now);
            userRoleMapper.insert(userRole);
        }
    }

    /** 为用户追加一个角色，已拥有时保持幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public void addRole(Long userId, String roleCode) {
        String normalizedCode = normalizeRoleCode(roleCode);
        if (getRoleCodes(userId).contains(normalizedCode)) {
            return;
        }
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getCode, normalizedCode));
        if (role == null) {
            throw new IllegalStateException("角色未初始化: " + normalizedCode);
        }
        userRoleMapper.insertIfAbsent(userId, role.getId());
    }

    /** 返回拥有指定角色的用户数量。 */
    public long countUsersByRole(String roleCode) {
        return userRoleMapper.countUsersByRoleCode(normalizeRoleCode(roleCode));
    }

    /** 返回拥有指定角色的用户 ID。 */
    public List<Long> findUserIdsByRole(String roleCode) {
        return userRoleMapper.selectUserIdsByRoleCode(normalizeRoleCode(roleCode));
    }

    /** 锁定并返回拥有指定角色的用户 ID；调用方必须处于事务中。 */
    public List<Long> lockUserIdsByRole(String roleCode) {
        return userRoleMapper.lockUserIdsByRoleCode(normalizeRoleCode(roleCode));
    }

    /** 锁定并返回拥有指定角色且未逻辑删除的用户 ID；调用方必须处于事务中。 */
    public List<Long> lockNotDeletedUserIdsByRole(String roleCode) {
        return userRoleMapper.lockNotDeletedUserIdsByRoleCode(normalizeRoleCode(roleCode));
    }

    /** 批量返回用户 ID 到规范化角色代码列表的映射。 */
    public Map<Long, List<String>> getRoleCodesByUserIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRoleMapper.selectRoleAssignmentsByUserIds(userIds).stream()
                .collect(Collectors.groupingBy(
                        UserRoleAssignment::getUserId,
                        Collectors.mapping(UserRoleAssignment::getRoleCode, Collectors.toList())));
    }

    /** 标准化角色名称，同时兼容 Spring Security 的 ROLE_ 前缀。 */
    public static String normalizeRoleCode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        if (!ROLE_PRIORITY.contains(normalized)) {
            throw new IllegalArgumentException("不支持的角色代码: " + value);
        }
        return normalized;
    }

    private Set<String> normalizeRoleCodes(Collection<String> roleCodes) {
        Set<String> normalized = new LinkedHashSet<>();
        if (roleCodes != null) {
            for (String roleCode : roleCodes) {
                normalized.add(normalizeRoleCode(roleCode));
            }
        }
        return normalized;
    }
}
