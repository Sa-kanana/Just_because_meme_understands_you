package com.sakana.just_because_meme_understands_you.modules.meme.contract;

import java.util.List;

/** 热梗候选来源的端口；实际 HTTP 客户端由集成层提供。 */
public interface MemeCandidateSource {

    /** 判断外部候选来源是否具备调用配置。 */
    boolean isConfigured();

    /** 获取指定数量的候选梗图。 */
    List<Candidate> fetchCandidates(int limit);

    /** 获取候选梗图，并向来源端提供已经入库的 B 站视频 ID 以跳过重复处理。 */
    default List<Candidate> fetchCandidates(int limit, List<String> excludedBilibiliVideoIds) {
        return fetchCandidates(limit);
    }

    /** 与外部采集 Agent 解耦的候选数据。 */
    record Candidate(
            String title,
            String introduction,
            String sourceUrl,
            String imageUrl,
            String detailUrl) {
    }
}
