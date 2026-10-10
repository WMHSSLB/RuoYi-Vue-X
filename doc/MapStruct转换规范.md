# MapStruct 转换规范

## 目标

新业务统一使用 MapStruct 处理 DTO、Entity、VO 之间的字段复制，避免在 Service 中反复手写 `BeanUtils.copyProperties`。

## 依赖约定

后端统一在父 `pom.xml` 管理版本：

- `org.mapstruct:mapstruct`
- `org.mapstruct:mapstruct-processor`
- `org.projectlombok:lombok-mapstruct-binding`

业务模块需要直接使用 MapStruct 时，引入 `mapstruct`；编译期生成实现类时，引入 `mapstruct-processor` 和 `lombok-mapstruct-binding`，作用域使用 `provided`。

## 生成器产物

代码生成器会为每张业务表生成一个转换器：

```text
${packageName}.convert.${ClassName}Convert
```

生成示例：

```java
@Mapper(config = MapStructConfig.class)
public interface DemoConvert
{
    DemoConvert INSTANCE = Mappers.getMapper(DemoConvert.class);

    Demo toEntity(DemoQueryDto dto);

    Demo toEntity(DemoSaveDto dto);

    Demo toEntity(DemoEditDto dto);

    DemoVo toVo(Demo entity);

    List<DemoVo> toVoList(List<Demo> entities);
}
```

`MapStructConfig` 是项目统一转换配置，放在 `ruoyi-common` 模块：

```java
@MapperConfig(
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        builder = @Builder(disableBuilder = true),
        nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface MapStructConfig
{
}
```

`unmappedTargetPolicy = ReportingPolicy.IGNORE` 用于忽略 VO 中额外的回显字段，例如文件上传字段生成的 `xxxList`。这类字段由业务装配逻辑填充，不由基础字段转换负责。

`builder = @Builder(disableBuilder = true)` 用于禁用 Lombok `@Builder` 映射。若实体类继承 `BaseEntity`，使用 builder 可能只映射当前实体字段，导致 `createBy`、`updateBy`、`params` 等继承字段没有进入 Entity。统一禁用 builder 后，MapStruct 使用 setter 映射，行为更接近原来的 `BeanUtils.copyProperties`。

`nullValueIterableMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT` 用于统一处理集合转换，输入列表为 `null` 时返回空集合，避免接口列表数据出现 `null`。

业务转换器可以在自己的 `XxxConvert` 内使用 `@Mapping`、`expression` 或 `default` 方法补充特殊转换逻辑；只有极少数业务需要完全不同策略时，才不使用公共 `MapStructConfig`。

## Service 使用规范

ServiceImpl 中只保留业务流程，转换统一调用 `XxxConvert.INSTANCE`：

```java
Demo entity = DemoConvert.INSTANCE.toEntity(saveDto);
DemoVo vo = DemoConvert.INSTANCE.toVo(entity);
List<DemoVo> voList = DemoConvert.INSTANCE.toVoList(entityList);
```

不要在 ServiceImpl 中新增私有 `toEntity`、`toVo` 方法，也不要继续手写 `BeanUtils.copyProperties`。

## 文件字段说明

文件字段本身仍按普通字段转换，例如业务表中的 `file1` 保存 fileId 或逗号分隔 fileIds。

VO 中的 `file1List`、`file2List` 等文件回显列表，不由 MapStruct 负责转换，由 `IBusinessStorageFileService.fillStorageFileList(...)` 统一装配。

## 手写业务建议

手写新业务时推荐按以下包结构组织：

```text
domain   数据库实体
dto      SaveDto / EditDto / QueryDto
vo       接口返回对象
convert  MapStruct 转换器
service  业务接口
service.impl 业务实现
```

当字段名一致、类型一致时，直接依赖 MapStruct 默认映射即可。字段名不一致或需要特殊格式转换时，再在 `XxxConvert` 中显式增加 `@Mapping`。
