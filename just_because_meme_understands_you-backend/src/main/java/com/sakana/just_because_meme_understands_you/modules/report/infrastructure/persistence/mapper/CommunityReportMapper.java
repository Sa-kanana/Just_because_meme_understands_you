package com.sakana.just_because_meme_understands_you.modules.report.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.report.domain.entity.CommunityReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface CommunityReportMapper extends BaseMapper<CommunityReport> {
    /** 仅允许领取待处理记录，确保处理人、结果与时间只写入一次。 */
    @Update("UPDATE community_report SET status='RESOLVED', result=#{result}, handled_by=#{adminId}, " +
            "handling_note=#{note}, handled_at=#{handledAt} WHERE id=#{id} AND status='PENDING'")
    int resolvePending(@Param("id") Long id, @Param("result") String result,
                       @Param("adminId") Long adminId, @Param("note") String note,
                       @Param("handledAt") LocalDateTime handledAt);
}
