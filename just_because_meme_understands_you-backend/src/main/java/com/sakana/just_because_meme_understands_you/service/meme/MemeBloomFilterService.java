package com.sakana.just_because_meme_understands_you.service.meme;

import com.sakana.just_because_meme_understands_you.entity.Meme;
import com.sakana.just_because_meme_understands_you.mapper.MemeMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.List;

/**
 * 梗 id 布隆过滤器：用于评论/收藏等入口快速预判梗是否存在，避免无效 id 穿透到 DB。
 * <ul>
 *   <li>启动时从 DB 全量加载已发布梗 id，热构建过滤器</li>
 *   <li>新梗发布成功后同步 add，保证后续请求不被误拦</li>
 * </ul>
 * 注：布隆过滤器存在假阳性，调用方需在 mightContain=true 时再查 DB 兜底。
 *
 * @author sakana
 */
@Slf4j
@Service
public class MemeBloomFilterService {

    /** 预期容量，按需调整 */
    private static final int EXPECTED_INSERTIONS = 100_000;
    /** 哈希函数数量 */
    private static final int HASH_FUNCTIONS = 5;

    private final BitSet bitSet = new BitSet(bitSize(EXPECTED_INSERTIONS));
    private final int bitSize = bitSize(EXPECTED_INSERTIONS);

    @Resource
    private MemeMapper memeMapper;

    @PostConstruct
    public void init() {
        try {
            List<Meme> memes = memeMapper.selectList(null);
            if (memes == null || memes.isEmpty()) {
                log.info("梗布隆过滤器初始化完成，当前无梗数据");
                return;
            }
            int count = 0;
            for (Meme meme : memes) {
                if (meme != null && meme.getId() != null) {
                    add(meme.getId());
                    count++;
                }
            }
            log.info("梗布隆过滤器初始化完成，已加载 {} 个梗 id", count);
        } catch (Exception e) {
            log.warn("梗布隆过滤器初始化失败，将退化为直接查 DB", e);
        }
    }

    /**
     * 将梗 id 加入过滤器，发布成功后调用。
     */
    public synchronized void add(Integer memeId) {
        if (memeId == null) {
            return;
        }
        byte[] bytes = toBytes(memeId);
        for (int i = 0; i < HASH_FUNCTIONS; i++) {
            bitSet.set(Math.abs(hash(bytes, i)) % bitSize);
        }
    }

    /**
     * 判断梗 id 是否可能存在。
     * 返回 false 表示一定不存在；返回 true 仍需查 DB 兜底。
     */
    public synchronized boolean mightContain(Integer memeId) {
        if (memeId == null) {
            return false;
        }
        byte[] bytes = toBytes(memeId);
        for (int i = 0; i < HASH_FUNCTIONS; i++) {
            if (!bitSet.get(Math.abs(hash(bytes, i)) % bitSize)) {
                return false;
            }
        }
        return true;
    }

    private static int bitSize(int expectedInsertions) {
        return Math.max(expectedInsertions * 16, 1 << 20);
    }

    private byte[] toBytes(Integer memeId) {
        return String.valueOf(memeId).getBytes(StandardCharsets.UTF_8);
    }

    private int hash(byte[] bytes, int seed) {
        int h = seed;
        for (byte b : bytes) {
            h = h * 31 + b;
        }
        return h;
    }
}
