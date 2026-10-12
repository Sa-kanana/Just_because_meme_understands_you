package com.sakana.just_because_meme_understands_you.modules.oss.infrastructure.persistence.mapper;

import lombok.Data;

/** 中文注释：历史业务文件字段的只读分页投影，统一携带游标、引用业务ID和文件值。 */
@Data
public class LegacyFileAssetReferenceRow {

    /** 中文注释：旧业务记录主键，用于按主键断点续扫。 */
    private Long sourceId;

    /** 中文注释：新资产引用中的业务记录ID。 */
    private Long bizId;

    /** 中文注释：旧业务列中的对象键、OSS/CDN URL 或外部 URL。 */
    private String objectValue;

    /** 中文注释：待审核修改稿中的 JSON 快照，只有审核稿数据源使用。 */
    private String contentJson;
}
