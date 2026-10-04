package com.sakana.just_because_meme_understands_you.api;

import com.sakana.just_because_meme_understands_you.api.user.controller.MemeController;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeTagVO;
import com.sakana.just_because_meme_understands_you.common.response.Result;
import com.sakana.just_because_meme_understands_you.modules.meme.application.IMemeTagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemeTagControllerTest {

    @Mock
    private IMemeTagService memeTagService;

    @InjectMocks
    private MemeController memeController;

    @Test
    void listTagsKeepsRouteAndSuccessEnvelope() throws NoSuchMethodException {
        MemeTagVO tag = new MemeTagVO();
        tag.setId(5);
        tag.setName("reaction");
        List<MemeTagVO> tagOptions = List.of(tag);
        when(memeTagService.listTagOptions()).thenReturn(tagOptions);

        GetMapping mapping = MemeController.class.getMethod("listTags").getAnnotation(GetMapping.class);
        assertNotNull(mapping);
        assertArrayEquals(new String[]{"/memes/tags"}, mapping.value());

        Result<List<MemeTagVO>> result = memeController.listTags();

        assertEquals(Result.CODE_SUCCESS, result.getCode());
        assertEquals(Result.MSG_SUCCESS, result.getMessage());
        assertSame(tagOptions, result.getData());
        verify(memeTagService).listTagOptions();
    }
}
