package com.sakana.just_because_meme_understands_you.modules.user.application.account.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.UserAuth;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserAuthMapper;
import com.sakana.just_because_meme_understands_you.modules.user.application.account.IUserAuthService;
import org.springframework.stereotype.Service;

@Service
public class UserAuthServiceImpl extends ServiceImpl<UserAuthMapper, UserAuth> implements IUserAuthService {
}

