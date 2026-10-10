package com.ruoyi.common.mapstruct;

import org.mapstruct.Builder;
import org.mapstruct.MapperConfig;
import org.mapstruct.NullValueMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct 全局转换配置。
 * <p>
 * 统一忽略未映射目标字段，并禁用 Lombok builder 映射，避免继承自 BaseEntity 的字段被遗漏。
 *
 * @author 955Kane
 */
@MapperConfig(
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        builder = @Builder(disableBuilder = true),
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface MapStructConfig
{
}
