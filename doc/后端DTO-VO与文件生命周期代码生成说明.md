# 后端 DTO、VO 与文件生命周期代码生成说明

## 一、改造目标

后端代码生成器从“实体直进直出”调整为更清晰的接口对象分层：

```text
XxxSaveDto
XxxEditDto
XxxQueryDto
XxxVo
```

并根据代码生成页面中的字段配置，自动生成文件资产生命周期处理代码。

## 二、DTO/VO 字段生成规则

字段来源按代码生成页面的勾选项决定：

| 生成对象 | 字段来源 |
| --- | --- |
| `XxxVo` | “列表”勾选为 true 的字段，另强制包含主键 |
| `XxxSaveDto` | “插入”勾选为 true 的字段；自增主键不生成，非自增主键可生成 |
| `XxxEditDto` | “编辑”勾选为 true 的字段，另强制包含主键 |
| `XxxQueryDto` | “查询”勾选为 true 的字段 |

分页参数仍沿用若依原有 `pageNum/pageSize/orderByColumn/isAsc/reasonable`，通过 `BaseEntity` 和 `startPage()` 处理。

## 三、VO 文件回显字段规则

如果字段同时满足：

```text
列表 = true
显示类型 = imageUpload 或 fileUpload
```

则 `XxxVo` 会额外生成文件回显列表：

```java
private List<StorageFileUploadRespDTO> file1List;
```

字段名按原字段追加 `List`，例如：

```text
file1 -> file1List
contractFile -> contractFileList
```

这个字段用于前端 Storage 上传组件的 `storage-file-list` 回显。

## 四、文件生命周期自动生成规则

当字段显示类型选择为：

```text
imageUpload
fileUpload
```

代码生成器默认把该字段纳入文件资产生命周期管理。生成的 Service 实现会自动声明：

```java
private static final List<BusinessStorageFileField<Xxx>> STORAGE_FILE_FIELDS = Arrays.asList(
    BusinessStorageFileField.of("system:demo:file1", Xxx::getFile1, null)
);
```

如果该字段还满足“列表=true”，则同时生成 VO 回显映射：

```java
private static final List<BusinessStorageFileField<XxxVo>> VO_STORAGE_FILE_FIELDS = Arrays.asList(
    BusinessStorageFileField.of("system:demo:file1", XxxVo::getFile1, XxxVo::setFile1List)
);
```

生成的 `bizType` 约定为：

```text
模块名:业务名:字段名
```

例如：

```text
system:node:file1
```

## 五、生成代码处理的生命周期场景

| 场景 | 生成逻辑 |
| --- | --- |
| 查询列表 | 填充 VO 的 `xxxList` |
| 查询详情 | 填充 VO 的 `xxxList` |
| 新增成功后 | 绑定表单提交的 fileId/fileIds 到当前业务 ID |
| 修改成功后 | 对比新旧 fileId/fileIds，同步绑定关系，移除的文件标记待删除 |
| 删除成功后 | 按业务 ID 和文件业务类型标记待删除 |

## 六、Controller 和 Service 生成规则

Controller 生成规则：

| 接口 | 入参 | 返回 |
| --- | --- | --- |
| 分页列表 | `XxxQueryDto` | `TableDataInfo`，rows 为 `XxxVo` |
| 树列表 | `XxxQueryDto` | `Response<List<XxxVo>>` |
| 详情 | 主键 | `Response<XxxVo>` |
| 新增 | `XxxSaveDto` | `Response<Void>` |
| 修改 | `XxxEditDto` | `Response<Void>` |
| 删除 | 主键数组 | `Response<Void>` |
| 导出 | `XxxQueryDto` | 实体列表导出 |

Service 生成规则：

1. 查询列表和详情时，实体转换为 VO。
2. 新增 DTO、编辑 DTO、查询 DTO 分别转换为实体。
3. 导出继续使用实体，保留实体上的 Excel 注解语义。
4. 有文件字段时，新增、修改、删除方法自动加事务。

## 七、相关模板文件

```text
ruoyi-generator/src/main/resources/vm/java/saveDto.java.vm
ruoyi-generator/src/main/resources/vm/java/editDto.java.vm
ruoyi-generator/src/main/resources/vm/java/queryDto.java.vm
ruoyi-generator/src/main/resources/vm/java/vo.java.vm
ruoyi-generator/src/main/resources/vm/java/controller.java.vm
ruoyi-generator/src/main/resources/vm/java/service.java.vm
ruoyi-generator/src/main/resources/vm/java/serviceImpl.java.vm
ruoyi-generator/src/main/java/com/ruoyi/generator/util/VelocityUtils.java
```

旧的单一 `dto.java.vm` 已删除，避免后续生成对象职责混淆。
