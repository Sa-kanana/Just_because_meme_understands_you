package com.sakana.just_because_meme_understands_you.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakana.just_because_meme_understands_you.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.service.IMemeResourceService;
import org.springframework.stereotype.Service;

/**
 * 梗相关链接服务实现
 *
 * @author sakana
 * @since 2026-07-02
 */
@Service
public class MemeResourceServiceImpl extends ServiceImpl<MemeResourceMapper, MemeResource> implements IMemeResourceService {

}
