package com.sakana.just_because_meme_understands_you.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.dto.MemeTagBindDTO;
import com.sakana.just_because_meme_understands_you.entity.MemeTagRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 梗-标签关系 Mapper
 *
 * @author sakana
 * @since 2026-03-06
 */
@Mapper
public interface MemeTagRelationMapper extends BaseMapper<MemeTagRelation> {

    /**
     * 一次 JOIN 查询批量取回多个梗的标签，按 meme_id 分组即可，避免 N+1。
     *
     * @param memeIds 梗 id 列表，不能为空
     * @return 标签关联行列表
     */
    List<MemeTagBindDTO> selectTagsByMemeIds(@Param("memeIds") List<Integer> memeIds);
}
