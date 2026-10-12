package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.sakana.just_because_meme_understands_you.modules.admin.application.assembler.AdminViewMapper;
import com.sakana.just_because_meme_understands_you.modules.moderation.domain.entity.SensitiveWord;
import com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.persistence.mapper.SensitiveWordMapper;
import com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.persistence.mapper.SensitiveWordDictionaryVersionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSensitiveWordReloadTest {

    @Test
    void publishesReloadOnlyAfterSensitiveWordUpdateCommits() {
        SensitiveWordMapper mapper = mock(SensitiveWordMapper.class);
        AdminViewMapper viewMapper = mock(AdminViewMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SensitiveWordDictionaryVersionMapper versionMapper = mock(SensitiveWordDictionaryVersionMapper.class);
        AdminSensitiveWordServiceImpl service = new AdminSensitiveWordServiceImpl();
        ReflectionTestUtils.setField(service, "sensitiveWordMapper", mapper);
        ReflectionTestUtils.setField(service, "adminViewMapper", viewMapper);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "versionMapper", versionMapper);

        SensitiveWord word = new SensitiveWord();
        word.setId(42L);
        word.setStatus(1);
        when(mapper.selectById(42L)).thenReturn(word);
        when(versionMapper.incrementVersion()).thenReturn(1);
        when(versionMapper.selectVersion()).thenReturn(11L);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.updateStatus(42L, 0);

            verify(redisTemplate, never()).convertAndSend("sensitive_word_clear", "11");

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            synchronizations.forEach(TransactionSynchronization::afterCommit);

            verify(redisTemplate).convertAndSend("sensitive_word_clear", "11");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
