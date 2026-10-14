package com.sakana.just_because_meme_understands_you.modules.meme.application;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCandidateSource;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeIndexEventPort;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeResource;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.config.MemeCrawlProperties;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeResourceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemeCrawlBloomFilterTest {

    @Test
    void crawledMemeIsAddedToBloomFilterAfterPersistence() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "meme-crawl-test"), MemeResource.class);
        MemeMapper memeMapper = mock(MemeMapper.class);
        MemeResourceMapper memeResourceMapper = mock(MemeResourceMapper.class);
        MemeCandidateSource candidateSource = mock(MemeCandidateSource.class);
        MemeIndexEventPort indexEventPort = mock(MemeIndexEventPort.class);
        MemeBloomFilterService bloomFilterService = new MemeBloomFilterService();
        MemeCrawlProperties properties = new MemeCrawlProperties();
        properties.setEnabled(true);
        properties.setLimit(1);
        properties.setPublisherUserId(88L);

        when(candidateSource.isConfigured()).thenReturn(true);
        when(candidateSource.fetchCandidates(1, List.of())).thenReturn(List.of(
                new MemeCandidateSource.Candidate(
                        "新采集的梗", "采集介绍", "https://example.com/source", null, null)));
        when(memeResourceMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(memeResourceMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(memeMapper.insert(any(Meme.class))).thenAnswer(invocation -> {
            ((Meme) invocation.getArgument(0)).setId(7001L);
            return 1;
        });
        when(memeResourceMapper.insert(any(MemeResource.class))).thenReturn(1);

        MemeCrawlService service = new MemeCrawlService();
        ReflectionTestUtils.setField(service, "memeCrawlProperties", properties);
        ReflectionTestUtils.setField(service, "memeCandidateSource", candidateSource);
        ReflectionTestUtils.setField(service, "memeMapper", memeMapper);
        ReflectionTestUtils.setField(service, "memeResourceMapper", memeResourceMapper);
        ReflectionTestUtils.setField(service, "memeIndexEventPort", indexEventPort);
        ReflectionTestUtils.setField(service, "transactionTemplate", new ImmediateTransactionTemplate());
        ReflectionTestUtils.setField(service, "memeBloomFilterService", bloomFilterService);

        var result = service.crawlAndPersist();

        assertEquals(1, result.getCreated());
        assertTrue(bloomFilterService.mightContain(7001L));
    }

    private static final class ImmediateTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(new SimpleTransactionStatus());
        }
    }
}
