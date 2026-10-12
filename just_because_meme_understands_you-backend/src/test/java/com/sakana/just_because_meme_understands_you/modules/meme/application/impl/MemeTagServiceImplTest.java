package com.sakana.just_because_meme_understands_you.modules.meme.application.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.meme.application.assembler.MemeViewMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemeTagServiceImplTest {

    @Mock
    private MemeTagMapper memeTagMapper;

    @Spy
    private MemeViewMapper memeViewMapper = Mappers.getMapper(MemeViewMapper.class);

    @InjectMocks
    private MemeTagServiceImpl service;

    @Test
    void listTagOptionsReturnsEmptyWhenMapperReturnsNull() {
        when(memeTagMapper.selectList(ArgumentMatchers.<Wrapper<MemeTag>>any())).thenReturn(null);

        assertEquals(List.of(), service.listTagOptions());

        verify(memeTagMapper).selectList(any());
    }

    @Test
    void listTagOptionsReturnsEmptyWhenMapperReturnsEmpty() {
        when(memeTagMapper.selectList(ArgumentMatchers.<Wrapper<MemeTag>>any())).thenReturn(List.of());

        assertEquals(List.of(), service.listTagOptions());

        verify(memeTagMapper).selectList(any());
    }

    @Test
    void listTagOptionsMapsIdAndNameInOrder() {
        MemeTag first = tag(11, "zeta");
        MemeTag nullableFields = tag(null, null);
        MemeTag middle = tag(4, "alpha");
        MemeTag repeated = tag(11, "zeta");
        when(memeTagMapper.selectList(ArgumentMatchers.<Wrapper<MemeTag>>any()))
                .thenReturn(List.of(first, nullableFields, middle, repeated));

        List<MemeTagVO> result = service.listTagOptions();

        assertEquals(4, result.size());
        assertEquals(11, result.get(0).getId());
        assertEquals("zeta", result.get(0).getName());
        assertNull(result.get(1).getId());
        assertNull(result.get(1).getName());
        assertEquals(4, result.get(2).getId());
        assertEquals("alpha", result.get(2).getName());
        assertEquals(11, result.get(3).getId());
        assertEquals("zeta", result.get(3).getName());
        verify(memeTagMapper).selectList(any());
    }

    private static MemeTag tag(Integer id, String name) {
        return new MemeTag().setId(id).setName(name);
    }
}
