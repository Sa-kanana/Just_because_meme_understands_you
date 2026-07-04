package com.sakana.just_because_meme_understands_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.entity.UserAuth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户认证信息 Mapper
 *
 * @author sakana
 */
@Mapper
public interface UserAuthMapper extends BaseMapper<UserAuth> {

    /**
     * 按登录类型与标识精确查询一条认证记录，无结果返回 null。
     * 替代 Service 层 LambdaQueryWrapper + .last("LIMIT 1") 的重复写法。
     *
     * @param identityType 登录类型（如 email）
     * @param identifier   标识（如邮箱）
     * @return UserAuth 或 null
     */
    UserAuth selectByIdentity(@Param("identityType") String identityType,
                              @Param("identifier") String identifier);

    /**
     * 按用户 id 与登录类型查询一条认证记录，无结果返回 null。
     *
     * @param userId       用户 id
     * @param identityType 登录类型
     * @return UserAuth 或 null
     */
    UserAuth selectByUserIdAndType(@Param("userId") Long userId,
                                   @Param("identityType") String identityType);
}
