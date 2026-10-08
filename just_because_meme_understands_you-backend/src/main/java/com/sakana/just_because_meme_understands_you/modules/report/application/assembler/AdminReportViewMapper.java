package com.sakana.just_because_meme_understands_you.modules.report.application.assembler;

import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminReportVO;
import com.sakana.just_because_meme_understands_you.common.mapping.MapStructConfig;
import com.sakana.just_because_meme_understands_you.modules.report.domain.entity.CommunityReport;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** 后台举报记录到 API 视图的基础字段映射器。 */
@Mapper(config = MapStructConfig.class)
public interface AdminReportViewMapper {

    /** 映射已存储的举报字段；目标内容和用户名称由应用服务查询补全。 */
    @Mapping(target = "memeName", ignore = true)
    @Mapping(target = "memeStatus", ignore = true)
    @Mapping(target = "targetAuthorId", ignore = true)
    @Mapping(target = "targetAuthorName", ignore = true)
    @Mapping(target = "commentContent", ignore = true)
    @Mapping(target = "commentAuthorId", ignore = true)
    @Mapping(target = "commentAuthorName", ignore = true)
    @Mapping(target = "reporterName", ignore = true)
    AdminReportVO toAdminReportVO(CommunityReport source);
}
