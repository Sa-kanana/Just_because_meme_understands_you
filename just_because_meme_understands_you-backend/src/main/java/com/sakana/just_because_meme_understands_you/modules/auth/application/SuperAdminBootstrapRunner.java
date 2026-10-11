package com.sakana.just_because_meme_understands_you.modules.auth.application;

import com.sakana.just_because_meme_understands_you.modules.user.application.UserRoleService;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserAuth;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserAuthMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** 可选的一次性引导：仅在尚无超级管理员时提升明确配置的已有邮箱账号。 */
@Component
public class SuperAdminBootstrapRunner implements ApplicationRunner {

    @Value("${auth.bootstrap-super-admin-email:}")
    private String bootstrapEmail;

    @Resource
    private UserRoleService userRoleService;

    @Resource
    private UserAuthMapper userAuthMapper;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(bootstrapEmail)
                || userRoleService.countUsersByRole(UserRoleService.SUPER_ADMIN) > 0) {
            return;
        }
        String normalizedEmail = bootstrapEmail.trim().toLowerCase(Locale.ROOT);
        UserAuth userAuth = userAuthMapper.selectByIdentity("email", normalizedEmail);
        if (userAuth == null || userAuth.getUserId() == null) {
            throw new IllegalStateException(
                    "auth.bootstrap-super-admin-email 指定的邮箱尚未注册：" + normalizedEmail);
        }
        userRoleService.addRole(userAuth.getUserId(), UserRoleService.SUPER_ADMIN);
    }
}
