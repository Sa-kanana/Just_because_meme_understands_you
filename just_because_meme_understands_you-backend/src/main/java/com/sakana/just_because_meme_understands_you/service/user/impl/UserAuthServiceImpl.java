package com.sakana.just_because_meme_understands_you.service.user.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakana.just_because_meme_understands_you.entity.UserAuth;
import com.sakana.just_because_meme_understands_you.mapper.UserAuthMapper;
import com.sakana.just_because_meme_understands_you.service.user.IUserAuthService;
import org.springframework.stereotype.Service;

@Service
public class UserAuthServiceImpl extends ServiceImpl<UserAuthMapper, UserAuth> implements IUserAuthService {
}

