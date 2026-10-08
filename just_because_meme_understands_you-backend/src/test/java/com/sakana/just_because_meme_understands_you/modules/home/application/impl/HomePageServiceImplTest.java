package com.sakana.just_because_meme_understands_you.modules.home.application.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sakana.just_because_meme_understands_you.api.user.vo.HomeBootstrapVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeListItemVO;
import com.sakana.just_because_meme_understands_you.common.pagination.PageVO;
import com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.config.MemeAgentProperties;
import com.sakana.just_because_meme_understands_you.modules.meme.application.IMemeService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomePageServiceImplTest {

    @Test
    void bootstrapExposesFeedTotalForPagination() {
        IMemeService memeService = mock(IMemeService.class);
        HomeHotTagSupport homeHotTagSupport = mock(HomeHotTagSupport.class);
        MemeAgentProperties memeAgentProperties = mock(MemeAgentProperties.class);
        HomePageServiceImpl service = new HomePageServiceImpl();
        ReflectionTestUtils.setField(service, "memeService", memeService);
        ReflectionTestUtils.setField(service, "homeHotTagSupport", homeHotTagSupport);
        ReflectionTestUtils.setField(service, "memeAgentProperties", memeAgentProperties);

        PageVO<MemeListItemVO> feedPage = new PageVO<>();
        feedPage.setList(List.of());
        feedPage.setPage(1);
        feedPage.setSize(12);
        feedPage.setTotal(25L);
        feedPage.setHasMore(true);

        when(homeHotTagSupport.listHotTags(10)).thenReturn(List.of());
        when(memeService.listHotMemes(6)).thenReturn(List.of());
        when(memeService.listLiveMemes(0L, 48, 8)).thenReturn(List.of());
        when(memeService.pageMemeFeed(1, 12, "hot", null)).thenReturn(feedPage);

        HomeBootstrapVO bootstrap = service.bootstrap(6, 10, "hot", 12);
        JsonNode feed = new ObjectMapper().valueToTree(bootstrap.getFeed());

        assertEquals(25L, feed.path("total").asLong());
    }
}
