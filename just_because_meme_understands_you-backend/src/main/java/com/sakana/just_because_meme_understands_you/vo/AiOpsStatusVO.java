package com.sakana.just_because_meme_understands_you.vo;

import lombok.Data;

/**
 * AI 运维状态面板。
 */
@Data
public class AiOpsStatusVO {

    /** Java 侧是否已配置 Agent baseUrl + apiKey */
    private boolean agentConfigured;

    /** Agent /health 是否可达 */
    private boolean agentReachable;

    /** Agent 上报的服务名 / env（可选） */
    private String agentService;

    private String agentEnv;

    /** Agent 是否连通向量库 */
    private Boolean vectorDbOk;

    private boolean crawlEnabled;

    private boolean crawlScheduleEnabled;

    private int crawlDefaultLimit;

    private boolean backfillEnabled;

    private String agentBaseUrl;
}
