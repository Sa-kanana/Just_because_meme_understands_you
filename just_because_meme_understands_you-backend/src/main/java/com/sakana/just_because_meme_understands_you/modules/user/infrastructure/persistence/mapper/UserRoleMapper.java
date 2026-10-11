package com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserRole;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.projection.UserRoleAssignment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 用户角色关联表数据访问。 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    /** 查询用户关联的角色代码，角色名称使用无 ROLE_ 前缀的规范形式。 */
    @Select("SELECT r.code FROM user_role ur JOIN `role` r ON r.id = ur.role_id WHERE ur.user_id = #{userId}")
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /** 查询拥有指定角色的用户总数，用于保护最后一个超级管理员。 */
    @Select("SELECT COUNT(DISTINCT ur.user_id) FROM user_role ur JOIN `role` r ON r.id = ur.role_id WHERE r.code = #{roleCode}")
    long countUsersByRoleCode(@Param("roleCode") String roleCode);

    /** 按角色代码查询用户 ID，用于管理端角色筛选。 */
    @Select("SELECT ur.user_id FROM user_role ur JOIN `role` r ON r.id = ur.role_id WHERE r.code = #{roleCode}")
    List<Long> selectUserIdsByRoleCode(@Param("roleCode") String roleCode);

    /** 在当前事务中锁定指定角色关联，串行化最后一个超级管理员的降权与禁用检查。 */
    @Select("SELECT ur.user_id FROM user_role ur JOIN `role` r ON r.id = ur.role_id WHERE r.code = #{roleCode} ORDER BY ur.user_id FOR UPDATE")
    List<Long> lockUserIdsByRoleCode(@Param("roleCode") String roleCode);

    /** 锁定未删除的指定角色用户；用于并发保护最后一个可恢复超级管理员。 */
    @Select("SELECT ur.user_id FROM user_role ur JOIN `role` r ON r.id = ur.role_id JOIN `user` u ON u.id = ur.user_id WHERE r.code = #{roleCode} AND u.deleted_at IS NULL ORDER BY ur.user_id FOR UPDATE")
    List<Long> lockNotDeletedUserIdsByRoleCode(@Param("roleCode") String roleCode);

    /** 批量查询用户角色，避免管理端分页逐用户查询关联表。 */
    @Select({
            "<script>",
            "SELECT ur.user_id AS userId, r.code AS roleCode",
            "FROM user_role ur JOIN `role` r ON r.id = ur.role_id",
            "WHERE ur.user_id IN",
            "<foreach collection='userIds' item='userId' open='(' separator=',' close=')'>#{userId}</foreach>",
            "ORDER BY CASE r.code WHEN 'SUPER_ADMIN' THEN 1 WHEN 'ADMIN' THEN 2 ELSE 3 END",
            "</script>"
    })
    List<UserRoleAssignment> selectRoleAssignmentsByUserIds(@Param("userIds") List<Long> userIds);

    /** 移除指定用户的全部角色关联，供事务内的完整角色替换使用。 */
    @Delete("DELETE FROM user_role WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);

    /** 幂等追加用户角色，支持多实例启动时的超级管理员引导。 */
    @Insert("INSERT IGNORE INTO user_role (user_id, role_id, create_time) VALUES (#{userId}, #{roleId}, CURRENT_TIMESTAMP)")
    int insertIfAbsent(@Param("userId") Long userId, @Param("roleId") Long roleId);
}
