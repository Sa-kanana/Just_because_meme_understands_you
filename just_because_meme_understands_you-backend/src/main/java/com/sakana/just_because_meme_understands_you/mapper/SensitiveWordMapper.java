package com.sakana.just_because_meme_understands_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感词 Mapper
 *
 * @author sakana
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {
}
