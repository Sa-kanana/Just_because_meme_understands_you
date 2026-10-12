package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.config.MemeCrawlProperties;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeEditRevisionMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemeCatalogPublishedKeysetPageTest {

    @Test
    void mapperDeclaresPublishedIdKeysetQuery() throws Exception {
        Method mapperMethod = Arrays.stream(MemeMapper.class.getMethods())
                .filter(method -> method.getName().equals("selectPublishedIdsAfterId"))
                .findFirst()
                .orElse(null);
        assertNotNull(mapperMethod, "应提供按 ID 游标查询已发布梗 ID 的 Mapper 方法");

        Select select = mapperMethod.getAnnotation(Select.class);
        assertNotNull(select, "已发布梗 ID 查询必须使用可检查的显式 SQL");
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ").toLowerCase();

        assertTrue(sql.matches(".*select\\s+id\\s+from\\s+meme\\b.*"),
                () -> "查询只能读取 ID 列: " + sql);
        assertTrue(sql.contains("status = 1"), () -> "查询必须过滤已发布状态: " + sql);
        assertTrue(sql.contains("id > #{afterid}"), () -> "查询必须使用递增 ID 游标: " + sql);
        assertTrue(sql.contains("order by id asc"), () -> "查询必须按 ID 升序: " + sql);
        assertTrue(sql.contains("limit #{limit}"), () -> "查询必须限制页大小: " + sql);
        assertTrue(!sql.contains("offset"), "查询不能使用 offset 分页");
    }

    @Test
    void catalogDeclaresPublishedIdPageContract() throws Exception {
        Method catalogMethod = Arrays.stream(MemeCatalogPort.class.getMethods())
                .filter(method -> method.getName().equals("pagePublishedIdsAfterId"))
                .findFirst()
                .orElse(null);
        assertNotNull(catalogMethod, "跨模块契约应提供已发布梗 ID 游标分页方法");
        assertTrue(catalogMethod.getReturnType().equals(java.util.List.class),
                "目录分页只返回梗 ID 列表");
    }

    @Test
    void pagePublishedIdsAfterIdReturnsOnlyIncreasingPublishedIds() {
        MemeMapper memeMapper = mock(MemeMapper.class);
        when(memeMapper.selectPublishedIdsAfterId(20L, 10)).thenReturn(List.of(21L, 22L));
        MemeCatalogApplicationAdapter adapter = adapter(memeMapper);

        List<Long> actual = adapter.pagePublishedIdsAfterId(20L, 10);

        assertTrue(actual.equals(List.of(21L, 22L)), "目录返回按 ID 升序的 ID 列表");
        verify(memeMapper).selectPublishedIdsAfterId(20L, 10);
    }

    @Test
    void pagePublishedIdsAfterIdClampsRequestedLimitAndCursor() {
        MemeMapper memeMapper = mock(MemeMapper.class);
        when(memeMapper.selectPublishedIdsAfterId(0L, 50)).thenReturn(List.of(1L));
        MemeCatalogApplicationAdapter adapter = adapter(memeMapper);

        List<Long> actual = adapter.pagePublishedIdsAfterId(-1L, 1000);

        assertTrue(actual.equals(List.of(1L)), "目录应透传 Mapper 的 ID 页");
        verify(memeMapper).selectPublishedIdsAfterId(0L, 50);
    }

    private MemeCatalogApplicationAdapter adapter(MemeMapper memeMapper) {
        return new MemeCatalogApplicationAdapter(
                memeMapper,
                mock(MemeEditRevisionMapper.class),
                mock(MemeTagMapper.class),
                mock(MemeTagRelationMapper.class),
                mock(MemeResourceMapper.class),
                mock(MemeBloomFilterService.class),
                mock(MemeCrawlProperties.class));
    }
}
