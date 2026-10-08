package com.sakana.just_because_meme_understands_you.common.mapping;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * 项目级 MapStruct 约定，确保 Mapper 由 Spring 管理并显式处理目标字段。
 */
@MapperConfig(
        // 生成 Spring Bean，供应用层通过依赖注入使用。
        componentModel = MappingConstants.ComponentModel.SPRING,
        // 使用构造器注入，便于检查 Mapper 的依赖。
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        // 目标类型新增字段时，要求映射器显式处理后才允许编译。
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        // 源类型存在 DTO 不需要暴露的字段时允许忽略。
        unmappedSourcePolicy = ReportingPolicy.IGNORE
)
public interface MapStructConfig {
}
