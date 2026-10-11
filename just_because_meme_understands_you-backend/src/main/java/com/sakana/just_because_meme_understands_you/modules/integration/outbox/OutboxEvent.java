package com.sakana.just_because_meme_understands_you.modules.integration.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 事务发件箱中的一条待投递集成事件。 */
@Data
@TableName("integration_outbox_event")
public class OutboxEvent {

    /** 发件箱事件主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务聚合类型。 */
    private String aggregateType;

    /** 业务聚合标识。 */
    private Long aggregateId;

    /** 集成事件类型。 */
    private String eventType;

    /** 事件载荷快照。 */
    private String payload;

    /** 投递状态。 */
    private String status;

    /** 已尝试投递次数。 */
    private Integer attempts;

    /** 事件创建时间。 */
    private LocalDateTime createdAt;

    /** 下次允许投递时间。 */
    private LocalDateTime nextAttemptAt;

    /** 本次处理租约时间。 */
    private LocalDateTime lockedAt;

    /** 成功投递时间。 */
    private LocalDateTime deliveredAt;

    /** 最近一次投递错误摘要。 */
    private String lastError;
}
