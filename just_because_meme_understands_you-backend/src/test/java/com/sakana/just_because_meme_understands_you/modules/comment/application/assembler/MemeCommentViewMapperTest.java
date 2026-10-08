package com.sakana.just_because_meme_understands_you.modules.comment.application.assembler;

import com.sakana.just_because_meme_understands_you.api.user.vo.MemeCommentCreateResponseVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeReplyCommentVO;
import com.sakana.just_because_meme_understands_you.api.user.vo.MemeRootCommentVO;
import com.sakana.just_because_meme_understands_you.modules.comment.domain.entity.MemeComment;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MemeCommentViewMapperTest {

    private final MemeCommentViewMapper mapper = Mappers.getMapper(MemeCommentViewMapper.class);

    @Test
    void mapsCreateResponseFields() {
        MemeComment source = comment();

        MemeCommentCreateResponseVO result = mapper.toCreateResponseVO(source);

        assertEquals(5L, result.getCommentId());
        assertEquals("评论内容", result.getContent());
        assertEquals(source.getCreateTime(), result.getCreateTime());
    }

    @Test
    void mapsCommentBaseFieldsAndLeavesContextFieldsForApplicationLayer() {
        MemeComment source = comment();

        MemeRootCommentVO root = mapper.toRootVO(source);
        MemeReplyCommentVO reply = mapper.toReplyVO(source);

        assertEquals(5L, root.getId());
        assertEquals(9L, root.getUserId());
        assertEquals("评论内容", root.getContent());
        assertNull(root.getUserName());
        assertEquals(5L, reply.getId());
        assertEquals(2L, reply.getParentId());
        assertEquals("评论内容", reply.getContent());
        assertNull(reply.getUserName());
    }

    private static MemeComment comment() {
        MemeComment source = new MemeComment();
        source.setId(5L);
        source.setParentId(2L);
        source.setUserId(9L);
        source.setContent("评论内容");
        source.setCreateTime(LocalDateTime.of(2026, 10, 8, 12, 0));
        return source;
    }
}
