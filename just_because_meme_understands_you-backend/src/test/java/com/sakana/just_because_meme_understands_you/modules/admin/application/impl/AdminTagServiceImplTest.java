package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.sakana.just_because_meme_understands_you.api.admin.dto.AdminTagSaveRequestDTO;
import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminTagVO;
import com.sakana.just_because_meme_understands_you.common.exception.BizException;
import com.sakana.just_because_meme_understands_you.modules.admin.application.assembler.AdminViewMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTag;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeTagMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 中文注释：确认管理端创建的标签必须使用受支持的分类，并保存分类顺序。 */
class AdminTagServiceImplTest {

    private MemeTagMapper memeTagMapper;
    private AdminTagServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        memeTagMapper = mock(MemeTagMapper.class);
        service = new AdminTagServiceImpl();
        inject("memeTagMapper", memeTagMapper);
        inject("adminViewMapper", Mappers.getMapper(AdminViewMapper.class));
        when(memeTagMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(memeTagMapper.insert(any(MemeTag.class))).thenAnswer(invocation -> {
            ((MemeTag) invocation.getArgument(0)).setId(40);
            return 1;
        });
    }

    @Test
    void createPersistsCategoryAndSortOrder() {
        AdminTagSaveRequestDTO request = new AdminTagSaveRequestDTO();
        request.setName("B站");
        request.setCategory("source");
        request.setSortOrder(12);

        AdminTagVO result = service.create(request);

        assertEquals("SOURCE", result.getCategory());
        assertEquals(12, result.getSortOrder());
        verify(memeTagMapper).insert(any(MemeTag.class));
    }

    @Test
    void createRejectsUnknownCategory() {
        AdminTagSaveRequestDTO request = new AdminTagSaveRequestDTO();
        request.setName("未知分类标签");
        request.setCategory("NOT_A_CATEGORY");

        assertThrows(BizException.class, () -> service.create(request));
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = AdminTagServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }
}
