package com.sakana.just_because_meme_understands_you.modules.ai.application;

import com.sakana.just_because_meme_understands_you.api.admin.vo.AiVectorReconciliationJobVO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.ai.domain.entity.MemeVectorReconciliationJob;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.persistence.mapper.MemeVectorReconciliationJobMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理端梗向量双向校准任务生命周期服务。 */
@Service
public class MemeVectorReconciliationService {

    private final MemeVectorReconciliationJobMapper jobMapper;

    public MemeVectorReconciliationService(MemeVectorReconciliationJobMapper jobMapper) {
        this.jobMapper = jobMapper;
    }

    /** 管理员手动启动全量校准，已有活动任务时返回当前任务。 */
    @Transactional
    public AiVectorReconciliationJobVO start(long adminId) {
        if (adminId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "管理员 ID 不合法");
        }
        // 中文注释：INSERT IGNORE 与 active_slot 唯一索引共同保证并发请求只创建一个活动任务。
        jobMapper.insertActiveJob(adminId);
        MemeVectorReconciliationJob activeJob = jobMapper.selectActiveJobForUpdate();
        if (activeJob == null) {
            throw new BizException(Result.CODE_ERROR, "校准任务创建失败，请稍后重试");
        }
        return toVO(activeJob);
    }

    /** 查询校准任务状态和已排队 outbox 事件数量。 */
    public AiVectorReconciliationJobVO find(long jobId) {
        return toVO(requireJob(jobMapper.selectById(jobId)));
    }

    /** 继续失败任务；不能与另一个活动任务并行。 */
    @Transactional
    public AiVectorReconciliationJobVO resume(long jobId, long adminId) {
        if (adminId <= 0) {
            throw new BizException(Result.CODE_BAD_REQUEST, "管理员 ID 不合法");
        }
        MemeVectorReconciliationJob job = requireJob(jobMapper.selectByIdForUpdate(jobId));
        if (!"FAILED".equals(job.getStatus())) {
            throw new BizException(Result.CODE_CONFLICT, "只有失败的校准任务可以继续");
        }
        if (jobMapper.resumeFailed(jobId) == 0) {
            throw new BizException(Result.CODE_CONFLICT, "已有其他校准任务正在运行，暂时无法继续");
        }
        return toVO(requireJob(jobMapper.selectById(jobId)));
    }

    private MemeVectorReconciliationJob requireJob(MemeVectorReconciliationJob job) {
        if (job == null) {
            throw new BizException(Result.CODE_NOT_FOUND, "校准任务不存在或已过期");
        }
        return job;
    }

    /** 中文注释：只向管理端公开任务进度和入队数量，不把入队数表述为向量写入数。 */
    private AiVectorReconciliationJobVO toVO(MemeVectorReconciliationJob job) {
        AiVectorReconciliationJobVO result = new AiVectorReconciliationJobVO();
        result.setJobId(job.getId());
        result.setStatus(job.getStatus());
        result.setPhase(job.getPhase());
        result.setPublishedScanned(job.getPublishedScanned());
        result.setUpsertQueued(job.getUpsertQueued());
        result.setVectorScanned(job.getVectorScanned());
        result.setRemovalQueued(job.getRemovalQueued());
        result.setFailureStage(job.getFailureStage());
        result.setCreatedAt(job.getCreatedAt());
        result.setUpdatedAt(job.getUpdatedAt());
        result.setFinishedAt(job.getFinishedAt());
        return result;
    }
}
