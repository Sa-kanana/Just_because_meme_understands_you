package com.sakana.just_because_meme_understands_you.modules.meme.application.impl;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.modules.meme.application.assembler.MemeViewMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.application.IMemeTagService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 梗标签应用查询实现。
 */
@Service
public class MemeTagServiceImpl implements IMemeTagService {

    private final MemeTagMapper memeTagMapper;
    private final MemeViewMapper memeViewMapper;

    public MemeTagServiceImpl(MemeTagMapper memeTagMapper, MemeViewMapper memeViewMapper) {
        this.memeTagMapper = memeTagMapper;
        this.memeViewMapper = memeViewMapper;
    }

    @Override
    public List<MemeTagVO> listTagOptions() {
        List<MemeTag> tags = memeTagMapper.selectList(null);
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        return tags.stream().map(memeViewMapper::toMemeTagVO).toList();
    }
}
