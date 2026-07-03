package com.sakana.just_because_meme_understands_you.service.user.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakana.just_because_meme_understands_you.entity.User;
import com.sakana.just_because_meme_understands_you.mapper.UserMapper;
import com.sakana.just_because_meme_understands_you.service.user.IUserService;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
}

