package com.sakana.just_because_meme_understands_you.modules.meme.application.impl;

import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTagRelation;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sakana.just_because_meme_understands_you.modules.meme.application.IMemeTagRelationService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author sakana
 * @since 2026-03-06
 */
@Service
public class MemeTagRelationServiceImpl extends ServiceImpl<MemeTagRelationMapper, MemeTagRelation> implements IMemeTagRelationService {

}
