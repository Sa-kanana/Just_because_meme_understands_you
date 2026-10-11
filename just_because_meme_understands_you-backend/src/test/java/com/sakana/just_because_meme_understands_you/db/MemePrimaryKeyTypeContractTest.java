package com.sakana.just_because_meme_understands_you.db;

import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.Meme;
import com.sakana.just_because_meme_understands_you.modules.meme.domain.entity.MemeTagRelation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 中文注释：验证梗主键及其关联字段与数据库 BIGINT 类型一致。 */
class MemePrimaryKeyTypeContractTest {

    @Test
    void memePrimaryKeyUsesLongForDatabaseBigint() throws Exception {
        assertEquals(Long.class, Meme.class.getDeclaredField("id").getType());
    }

    @Test
    void memeTagRelationMemeIdUsesLongForDatabaseBigint() throws Exception {
        assertEquals(Long.class, MemeTagRelation.class.getDeclaredField("memeId").getType());
    }
}
