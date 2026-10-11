package com.sakana.just_because_meme_understands_you.modules.admin.application.impl;

import com.sakana.just_because_meme_understands_you.api.admin.vo.AdminMemeDetailVO;
import com.sakana.just_because_meme_understands_you.modules.admin.application.assembler.AdminViewMapper;
import com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.infrastructure.persistence.mapper.MemeMapper;
import com.sakana.just_because_meme_understands_you.modules.oss.application.OssUrlHelper;
import com.sakana.just_because_meme_understands_you.modules.user.domain.entity.User;
import com.sakana.just_because_meme_understands_you.modules.user.infrastructure.persistence.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.Answers;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证管理端梗详情包含审核所需的标签和全部关联资源。 */
class AdminMemeDetailViewTest {

    @Test
    void detailIncludesTagsAndOfflineResourcesForModerationReview() throws Exception {
        Meme meme = new Meme();
        meme.setId(91L);
        meme.setName("测试梗");
        meme.setUserId(17L);
        meme.setImage("covers/meme.png");
        meme.setStatus(1);

        User author = new User();
        author.setId(17L);
        author.setNickname("测试作者");

        MemeMapper memeMapper = mock(MemeMapper.class);
        when(memeMapper.selectById(91L)).thenReturn(meme);
        UserMapper userMapper = mock(UserMapper.class);
        when(userMapper.selectBatchIds(any(Collection.class))).thenReturn(List.of(author));
        OssUrlHelper ossUrlHelper = mock(OssUrlHelper.class);
        when(ossUrlHelper.toPublicUrl(any())).thenAnswer(invocation ->
                "https://cdn.example/" + invocation.getArgument(0, String.class));

        MemeCatalogPort memeCatalogPort = mock(MemeCatalogPort.class, invocation -> {
            if (invocation.getMethod().getName().equals("findTagsByMemeIds")) {
                return Map.of(91L, List.of(new MemeCatalogPort.MemeTagSummary(6L, "测试标签", 4)));
            }
            if (invocation.getMethod().getName().equals("findResourcesByMemeId")) {
                return List.of(archivedResourceSummary());
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });

        AdminMemeServiceImpl service = new AdminMemeServiceImpl();
        ReflectionTestUtils.setField(service, "memeMapper", memeMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "adminViewMapper", Mappers.getMapper(AdminViewMapper.class));
        ReflectionTestUtils.setField(service, "ossUrlHelper", ossUrlHelper);
        ReflectionTestUtils.setField(service, "memeCatalogPort", memeCatalogPort);

        AdminMemeDetailVO detail = service.detail(91L);

        List<?> tags = (List<?>) readProperty(detail, "tags");
        assertEquals(1, tags.size());
        assertEquals(6L, readProperty(tags.get(0), "id"));
        assertEquals("测试标签", readProperty(tags.get(0), "name"));

        List<?> resources = (List<?>) readProperty(detail, "resources");
        assertEquals(1, resources.size());
        assertEquals("image", readProperty(resources.get(0), "type"));
        assertEquals("https://cdn.example/resources/archived.png", readProperty(resources.get(0), "url"));
        assertEquals(0, readProperty(resources.get(0), "status"));
    }

    private static Object archivedResourceSummary() throws Exception {
        Class<?> summaryType = Class.forName(
                "com.sakana.just_because_meme_understands_you.modules.meme.contract.MemeCatalogPort$MemeResourceSummary");
        Constructor<?> constructor = summaryType.getDeclaredConstructors()[0];
        return constructor.newInstance(104L, 5, "历史图片", "resources/archived.png", 2, 0);
    }

    private static Object readProperty(Object target, String name) throws Exception {
        String getterName = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
        Method getter = target.getClass().getMethod(getterName);
        return getter.invoke(target);
    }
}
