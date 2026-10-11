package com.sakana.just_because_meme_understands_you.modules.ai.application;

import com.sakana.just_because_meme_understands_you.api.user.dto.AiSearchStreamRequestDTO;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.AiStreamTaskSnapshot;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Optional;

/** 面向用户的后台 AI 流式任务生命周期接口。 */
public interface IAiStreamTaskService {

    /** 创建或按请求 ID 复用任务，并独立启动后台生成。 */
    AiStreamTaskSnapshot submit(Long userId, AiSearchStreamRequestDTO request);

    /** 查询当前用户最近一个仍在保留期内的任务。 */
    Optional<AiStreamTaskSnapshot> getCurrent(Long userId);

    /** 按事件 ID 游标读取任务事件并持续订阅新事件。 */
    Flux<ServerSentEvent<String>> events(Long userId, String taskId, long afterEventId);

    /** 显式取消用户拥有的运行中任务。 */
    AiStreamTaskSnapshot cancel(Long userId, String taskId);

    /** 会话删除成功后取消该会话仍在运行的任务。 */
    void cancelBySession(Long userId, Long sessionId);
}
