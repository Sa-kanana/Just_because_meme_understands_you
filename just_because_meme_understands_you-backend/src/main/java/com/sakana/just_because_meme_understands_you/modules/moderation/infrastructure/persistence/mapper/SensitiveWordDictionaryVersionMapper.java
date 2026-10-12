package com.sakana.just_because_meme_understands_you.modules.moderation.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 中文注释：维护敏感词词库的全局修订版本。 */
@Mapper
public interface SensitiveWordDictionaryVersionMapper {

    /** 中文注释：查询单例词库版本行的当前版本号。 */
    @Select("SELECT version FROM sensitive_word_dictionary_meta WHERE id = 1")
    Long selectVersion();

    /** 中文注释：在当前事务中原子递增词库版本号。 */
    @Update("UPDATE sensitive_word_dictionary_meta SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = 1")
    int incrementVersion();
}
