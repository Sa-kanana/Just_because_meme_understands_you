package com.sakana.just_because_meme_understands_you.controller;

import com.sakana.just_because_meme_understands_you.common.Result;
import com.sakana.just_because_meme_understands_you.common.support.AuthContext;
import com.sakana.just_because_meme_understands_you.dto.MemeCreateRequestDTO;
import com.sakana.just_because_meme_understands_you.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.service.meme.MemePublishService;
import com.sakana.just_because_meme_understands_you.vo.MemeCreateResponseVO;
import com.sakana.just_because_meme_understands_you.vo.MemeTagVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 梗发布接口
 *
 * @author sakana
 */
@RestController
public class MemeController {

    @Resource
    private MemePublishService memePublishService;

    @Resource
    private MemeTagMapper memeTagMapper;

    /**
     * 获取所有可用标签，供发布梗时选择
     * GET /memes/tags
     */
    @GetMapping("/memes/tags")
    public Result<List<MemeTagVO>> listTags() {
        List<MemeTag> tags = memeTagMapper.selectList(null);
        List<MemeTagVO> voList = tags == null ? List.of() : tags.stream().map(t -> {
            MemeTagVO vo = new MemeTagVO();
            vo.setId(t.getId());
            vo.setName(t.getName());
            return vo;
        }).toList();
        return Result.success(voList);
    }

    /**
     * 发布梗（需登录）
     * POST /memes
     * 图片资源采用两阶段法：前端先调 /oss/policy 直传 OSS 拿到 URL，再随表单提交。
     */
    @PostMapping("/memes")
    public Result<MemeCreateResponseVO> publish(@RequestBody MemeCreateRequestDTO request,
                                                 HttpServletRequest httpServletRequest) {
        Long userId = AuthContext.requireCurrentUserId(httpServletRequest);
        return Result.success(memePublishService.publish(userId, request));
    }
}
