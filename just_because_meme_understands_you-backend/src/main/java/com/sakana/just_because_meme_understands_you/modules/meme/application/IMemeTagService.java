package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;

import java.util.List;

/**
 * 梗标签应用查询。
 */
public interface IMemeTagService {

    /**
     * 查询可供梗发布页面选择的标签。
     *
     * @return 标签选项，保持持久层返回顺序
     */
    List<MemeTagVO> listTagOptions();
}
