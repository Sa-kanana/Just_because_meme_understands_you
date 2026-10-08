package com.sakana.just_because_meme_understands_you.modules.report.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sakana.just_because_meme_understands_you.modules.report.domain.entity.MemeReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface MemeReportMapper extends BaseMapper<MemeReport> {
    /** 条件更新实现一次性领取，事务回滚时释放领取。 */
    @Update("UPDATE meme_report SET status='RESOLVED', result=#{result}, handled_by=#{adminId}, " +
            "handling_note=#{note}, handled_at=#{handledAt} WHERE id=#{id} AND status='PENDING'")
    int resolvePending(@Param("id") Long id, @Param("result") String result,
                       @Param("adminId") Long adminId, @Param("note") String note,
                       @Param("handledAt") LocalDateTime handledAt);
}
