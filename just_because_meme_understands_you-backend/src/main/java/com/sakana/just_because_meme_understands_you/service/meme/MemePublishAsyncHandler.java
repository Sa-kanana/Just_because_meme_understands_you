package com.sakana.just_because_meme_understands_you.service.meme;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.sakana.just_because_meme_understands_you.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.mapper.MemeTagMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 发布/删除梗后的非核心异步处理：标签相关梗数量累加与回滚。
 * 通过本地异步线程池执行，不阻塞发布主事务。
 *
 * @author sakana
 */
@Slf4j
@Service
public class MemePublishAsyncHandler {

    @Resource
    private MemeTagMapper memeTagMapper;

    /**
     * 对命中的标签 related_quantity 执行 +1。
     * related_quantity 为 varchar 数字，MySQL 隐式转换支持 +1。
     *
     * @param tagIds 命中的标签 id
     */
    @Async("memePublishExecutor")
    public void incrementTagRelatedQuantity(List<Integer> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Integer tagId : tagIds) {
            if (tagId == null || tagId <= 0) {
                continue;
            }
            try {
                memeTagMapper.update(null, new LambdaUpdateWrapper<MemeTag>()
                        .eq(MemeTag::getId, tagId)
                        .setSql("related_quantity = related_quantity + 1"));
            } catch (Exception e) {
                log.warn("标签 related_quantity 累加失败, tagId={}", tagId, e);
            }
        }
    }

    /**
     * 删除梗时对关联标签 related_quantity 执行 -1（不低于 0）。
     */
    @Async("memePublishExecutor")
    public void decrementTagRelatedQuantity(List<Integer> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Integer tagId : tagIds) {
            if (tagId == null || tagId <= 0) {
                continue;
            }
            try {
                memeTagMapper.update(null, new LambdaUpdateWrapper<MemeTag>()
                        .eq(MemeTag::getId, tagId)
                        .setSql("related_quantity = GREATEST(CAST(related_quantity AS SIGNED) - 1, 0)"));
            } catch (Exception e) {
                log.warn("标签 related_quantity 回滚失败, tagId={}", tagId, e);
            }
        }
    }
}
