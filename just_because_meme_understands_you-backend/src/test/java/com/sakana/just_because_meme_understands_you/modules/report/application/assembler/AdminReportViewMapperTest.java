package com.sakana.just_because_meme_understands_you.modules.report.application.assembler;

import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminReportVO;
import com.sakana.just_because_meme_understands_you.modules.report.domain.entity.CommunityReport;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AdminReportViewMapperTest {

    private final AdminReportViewMapper mapper = Mappers.getMapper(AdminReportViewMapper.class);

    @Test
    void mapsStoredReportFieldsAndLeavesTargetContextForApplicationLayer() {
        CommunityReport source = new CommunityReport();
        source.setId(4L);
        source.setTargetType("COMMENT");
        source.setTargetId(12L);
        source.setCommentId(12L);
        source.setReporterUserId(3L);
        source.setReason("骚扰");
        source.setStatus("PENDING");

        AdminReportVO result = mapper.toAdminReportVO(source);

        assertEquals(4L, result.getId());
        assertEquals("COMMENT", result.getTargetType());
        assertEquals(12L, result.getTargetId());
        assertEquals("骚扰", result.getReason());
        assertNull(result.getMemeName());
        assertNull(result.getReporterName());
    }
}
