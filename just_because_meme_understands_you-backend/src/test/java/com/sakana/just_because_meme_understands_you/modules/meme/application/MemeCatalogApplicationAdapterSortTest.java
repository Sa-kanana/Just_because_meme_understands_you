package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.config.MemeCrawlProperties;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeEditRevisionMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemeCatalogApplicationAdapterSortTest {

    @Mock
    private MemeMapper memeMapper;
    @Mock
    private MemeEditRevisionMapper memeEditRevisionMapper;
    @Mock
    private MemeTagMapper memeTagMapper;
    @Mock
    private MemeTagRelationMapper memeTagRelationMapper;
    @Mock
    private MemeResourceMapper memeResourceMapper;
    @Mock
    private MemeBloomFilterService memeBloomFilterService;
    @Mock
    private MemeCrawlProperties memeCrawlProperties;

    private MemeCatalogApplicationAdapter adapter;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Meme.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "resource-test"), MemeResource.class);
        adapter = new MemeCatalogApplicationAdapter(memeMapper, memeEditRevisionMapper,
                memeTagMapper, memeTagRelationMapper, memeResourceMapper,
                memeBloomFilterService, memeCrawlProperties);
    }

    @Test
    void sortsByRequestedMetricAndKeepsStableNewestTieBreakers() {
        assertOrder(MemeCatalogPort.MemeSort.VIEWS,
                "ORDER BY page_views DESC, release_time DESC, id DESC");
        assertOrder(MemeCatalogPort.MemeSort.LIKES,
                "ORDER BY likes DESC, release_time DESC, id DESC");
        assertOrder(MemeCatalogPort.MemeSort.COMMENTS,
                "ORDER BY comments DESC, release_time DESC, id DESC");
    }

    @Test
    void usesLatestOrderForDefaultAndInvalidSortValues() {
        assertSame(MemeCatalogPort.MemeSort.LATEST, MemeCatalogPort.MemeSort.fromValue(null));
        assertSame(MemeCatalogPort.MemeSort.LATEST, MemeCatalogPort.MemeSort.fromValue("untrusted-column"));
        assertSame(MemeCatalogPort.MemeSort.VIEWS, MemeCatalogPort.MemeSort.fromValue("Views"));
        assertOrder(MemeCatalogPort.MemeSort.LATEST,
                "ORDER BY release_time DESC, id DESC");
    }

    @Test
    void loadsAllResourcesInDisplayOrderIncludingOfflineResources() throws Exception {
        MemeResource resource = new MemeResource()
                .setId(104L)
                .setMemeId(91L)
                .setResourceUrl("resources/archived.png")
                .setResourceType(5)
                .setTitle("历史图片")
                .setSortOrder(2)
                .setStatus(0);
        when(memeResourceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(resource));
        ReflectionTestUtils.setField(adapter, "memeResourceMapper", memeResourceMapper);

        Method query = MemeCatalogPort.class.getMethod("findResourcesByMemeId", long.class);
        List<?> resources = (List<?>) query.invoke(adapter, 91L);

        assertEquals("resources/archived.png", readProperty(resources.get(0), "resourceUrl"));
        assertEquals(0, readProperty(resources.get(0), "status"));
        ArgumentCaptor<Wrapper<MemeResource>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(memeResourceMapper).selectList(wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().replaceAll("\\s+", "").toLowerCase();
        assertTrue(sql.contains("meme_id"));
        assertTrue(sql.contains("orderbysort_orderasc,idasc"), () -> "资源排序不正确: " + sql);
        assertFalse(sql.contains("status"), "管理员详情需要看到已下架或归档的历史资源");
    }

    private static Object readProperty(Object target, String name) throws Exception {
        try {
            return target.getClass()
                    .getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1))
                    .invoke(target);
        } catch (NoSuchMethodException ignored) {
            return target.getClass().getMethod(name).invoke(target);
        }
    }

    private void assertOrder(MemeCatalogPort.MemeSort sort, String expectedOrder) {
        clearInvocations(memeMapper);
        when(memeMapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<Meme> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });
        adapter.pageByAuthor(7L, false, null, 1, 12, sort);

        ArgumentCaptor<Wrapper<Meme>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(memeMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String actualOrder = wrapperCaptor.getValue().getSqlSegment().replace(" ", "").toUpperCase();
        assertTrue(actualOrder.contains(expectedOrder.replace(" ", "").toUpperCase()),
                () -> "Expected order " + expectedOrder + " but got " + wrapperCaptor.getValue().getSqlSegment());
    }
}
