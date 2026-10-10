# 文件字段VO和显式Facade接入说明

## 一、改造前存在的问题

上一版文件回显方案把文件字段注解和文件回显列表都放在业务实体上：

```java
@StorageFileField(bizType = "pointImage")
private String file1;

private List<StorageFileUploadRespDTO> file1List;
```

这种方式虽然能自动回填文件列表，但实体类同时承担了数据库映射、接口返回、文件回显配置三类职责

主要问题：

- Entity 依赖文件 DTO，数据库实体被接口返回结构污染
- Entity 上的 `@StorageFileField` 需要反射扫描，字段关系不够直观
- 不同业务文件字段数量不同，实体注解方式容易让生成代码和业务代码耦合
- 业务查询权限在 Controller/Service，文件 token 签发却由通用反射隐式触发，链路不够清晰

## 二、最终版方案

本次改造后，文件和业务关系保持不变：

- 继续保留 `sys_storage_file`
- 继续保留业务表中的 `fileId/fileIds` 字段
- 继续保留文件表中的 `bizType/bizId` 作为辅助管理字段
- Entity 只保留数据库字段，不再放 `@StorageFileField`
- Entity 不再放 `fileList`
- 查询和保存使用 DTO
- 接口返回使用 VO，VO 中放文件回显列表
- 业务 Service 显式调用文件 facade 填充 `fileList`
- 私有文件 token 只由业务查询接口签发
- `/common/file/download/{fileId}` 只负责校验 token 和输出文件流

## 三、当前点位模块链路

### 1. 列表查询链路

```text
GET /system/node/list
  -> NodeController.list(NodeQueryDTO)
  -> NodeServiceImpl.selectNodeList(NodeQueryDTO)
  -> NodeMapper.selectNodeList(Node)
  -> Node 转 NodeVo
  -> businessStorageFileService.fillStorageFileList(nodes, NODE_VO_FILE_FIELDS)
  -> sys_storage_file 批量查询文件记录
  -> 私有文件生成短期 token
  -> 返回 NodeVo 列表
```

### 2. 详情查询链路

```text
GET /system/node/{id}
  -> NodeController.getInfo(id)
  -> NodeServiceImpl.selectNodeById(id)
  -> NodeMapper.selectNodeById(id)
  -> Node 转 NodeVo
  -> businessStorageFileService.fillStorageFileList(nodeVo, NODE_VO_FILE_FIELDS)
  -> 返回 NodeVo
```

### 3. 新增绑定链路

```text
POST /system/node
  -> NodeController.add(NodeSaveDTO)
  -> NodeServiceImpl.insertNode(NodeSaveDTO)
  -> 保存业务表
  -> 使用生成后的业务ID作为 bizId
  -> businessStorageFileService.bindStorageFiles(entity, bizId, NODE_FILE_FIELDS, operator)
  -> sys_storage_file 从临时文件绑定为业务文件
```

### 4. 修改同步链路

```text
PUT /system/node
  -> NodeController.edit(NodeSaveDTO)
  -> NodeServiceImpl.updateNode(NodeSaveDTO)
  -> 先查询旧业务数据
  -> 更新业务表
  -> businessStorageFileService.syncStorageFiles(entity, oldNode, bizId, NODE_FILE_FIELDS, operator)
  -> 新增文件绑定业务，移除文件标记待删除
```

### 5. 删除清理链路

```text
DELETE /system/node/{ids}
  -> NodeController.remove(ids)
  -> NodeServiceImpl.deleteNodeByIds(ids)
  -> 删除业务表记录
  -> businessStorageFileService.markStorageFilesWaitDeleteByBiz(ids, NODE_FILE_FIELDS, operator)
  -> 将业务下声明的文件类型统一标记待删除
```

## 四、核心代码

### 1. Entity 只保留数据库字段

`Node` 中只保留 `file1` 到 `file5` 这类业务表字段，不再声明 `@StorageFileField`，也不再声明 `file1List` 到 `file5List`

```java
/** 图片 */
@ExcelProperty(value = "图片")
@Excel(name = "图片")
private String file1;

/** 附件 */
@ExcelProperty(value = "附件")
@Excel(name = "附件")
private String file2;

/** 富文本 */
@ExcelProperty(value = "富文本")
@Excel(name = "富文本")
private String file3;

/** 音频 */
@ExcelProperty(value = "音频")
@Excel(name = "音频")
private String file4;

/** 视频 */
@ExcelProperty(value = "视频")
@Excel(name = "视频")
private String file5;
```

### 2. 保存 DTO 接收文件ID

`NodeSaveDTO` 只接收业务保存字段，文件字段保存 `fileId` 或逗号分隔的 `fileIds`

```java
/**
 * 图片文件ID，多个文件使用逗号分隔
 */
private String file1;

/**
 * 附件文件ID，多个文件使用逗号分隔
 */
private String file2;

/**
 * 富文本文件ID，多个文件使用逗号分隔
 */
private String file3;

/**
 * 音频文件ID，多个文件使用逗号分隔
 */
private String file4;

/**
 * 视频文件ID，多个文件使用逗号分隔
 */
private String file5;
```

### 3. VO 承载文件回显列表

`NodeVo` 保留业务表中的文件ID字段，同时额外提供 `fileList` 给前端回显

```java
/**
 * 图片文件ID，多个文件使用逗号分隔
 */
private String file1;

/**
 * 图片文件回显列表
 */
private List<StorageFileUploadRespDTO> file1List;

/**
 * 附件文件ID，多个文件使用逗号分隔
 */
private String file2;

/**
 * 附件文件回显列表
 */
private List<StorageFileUploadRespDTO> file2List;
```

### 4. 业务 Service 显式声明文件字段映射

点位模块显式声明两组映射：

- `NODE_FILE_FIELDS` 用于新增、修改、删除时绑定或标记文件
- `NODE_VO_FILE_FIELDS` 用于列表、详情返回时填充文件回显列表

```java
/**
 * 点位实体文件字段映射
 * <p>
 * 业务表仍保存 fileId/fileIds，bizType 只作为文件资产辅助归类和生命周期管理维度
 */
private static final List<BusinessStorageFileField<Node>> NODE_FILE_FIELDS = Arrays.asList(
        BusinessStorageFileField.of(BIZ_TYPE_POINT_IMAGE, Node::getFile1, null),
        BusinessStorageFileField.of(BIZ_TYPE_POINT_ATTACHMENT, Node::getFile2, null),
        BusinessStorageFileField.of(BIZ_TYPE_POINT_RICH_TEXT, Node::getFile3, null),
        BusinessStorageFileField.of(BIZ_TYPE_POINT_AUDIO, Node::getFile4, null),
        BusinessStorageFileField.of(BIZ_TYPE_POINT_VIDEO, Node::getFile5, null));

/**
 * 点位返回对象文件回显字段映射
 * <p>
 * VO 承载 fileList 回显字段，避免数据库实体依赖文件 DTO
 */
private static final List<BusinessStorageFileField<NodeVo>> NODE_VO_FILE_FIELDS = Arrays.asList(
        BusinessStorageFileField.of(BIZ_TYPE_POINT_IMAGE, NodeVo::getFile1, NodeVo::setFile1List),
        BusinessStorageFileField.of(BIZ_TYPE_POINT_ATTACHMENT, NodeVo::getFile2, NodeVo::setFile2List),
        BusinessStorageFileField.of(BIZ_TYPE_POINT_RICH_TEXT, NodeVo::getFile3, NodeVo::setFile3List),
        BusinessStorageFileField.of(BIZ_TYPE_POINT_AUDIO, NodeVo::getFile4, NodeVo::setFile4List),
        BusinessStorageFileField.of(BIZ_TYPE_POINT_VIDEO, NodeVo::getFile5, NodeVo::setFile5List));
```

### 5. 查询时显式填充 VO 文件列表

```java
@Override
public List<NodeVo> selectNodeList(NodeQueryDTO node)
{
    List<NodeVo> nodes = toNodeVoList(nodeMapper.selectNodeList(toNode(node)));
    // 列表接口已经由若依分页和权限链路控制，文件服务按显式 mapping 批量生成授权地址
    businessStorageFileService.fillStorageFileList(nodes, NODE_VO_FILE_FIELDS);
    return nodes;
}
```

### 6. 新增时显式绑定文件

```java
@Override
@Transactional(rollbackFor = Exception.class)
public int insertNode(NodeSaveDTO node)
{
    Node entity = toNode(node);
    entity.setCreateTime(DateUtils.getNowDate());
    int rows = nodeMapper.insertNode(entity);
    if (rows > 0)
    {
        // 新增点位成功后，生成的点位ID作为 bizId 绑定本次上传的文件
        businessStorageFileService.bindStorageFiles(entity, String.valueOf(entity.getId()), NODE_FILE_FIELDS,
                businessStorageFileService.getOperator(entity));
    }
    return rows;
}
```

### 7. 修改时显式同步文件

```java
@Override
@Transactional(rollbackFor = Exception.class)
public int updateNode(NodeSaveDTO node)
{
    // 修改前先查询旧数据，用于判断哪些文件已经从表单中被移除
    Node oldNode = nodeMapper.selectNodeById(node.getId());
    Node entity = toNode(node);
    entity.setUpdateTime(DateUtils.getNowDate());
    int rows = nodeMapper.updateNode(entity);
    if (rows > 0 && oldNode != null)
    {
        // 修改成功后同步新旧文件集合，移除的文件会进入待删除状态
        businessStorageFileService.syncStorageFiles(entity, oldNode, String.valueOf(entity.getId()), NODE_FILE_FIELDS,
                businessStorageFileService.getOperator(entity));
    }
    return rows;
}
```

### 8. 删除时显式标记待删除

```java
@Override
@Transactional(rollbackFor = Exception.class)
public int deleteNodeByIds(Long[] ids)
{
    int rows = nodeMapper.deleteNodeByIds(ids);
    if (rows > 0)
    {
        // 批量删除点位后，将每个点位下各类文件统一标记为待删除
        businessStorageFileService.markStorageFilesWaitDeleteByBiz(ids, NODE_FILE_FIELDS,
                businessStorageFileService.getOperator(null));
    }
    return rows;
}
```

### 9. 通用文件 facade 新增显式映射 API

```java
public <T> void fillStorageFileList(T business, Collection<BusinessStorageFileField<T>> fields);

public <T> void fillStorageFileList(Collection<T> businesses, Collection<BusinessStorageFileField<T>> fields);

public <T> int bindStorageFiles(T business, String bizId, Collection<BusinessStorageFileField<T>> fields,
        String updateBy);

public <T> int syncStorageFiles(T newBusiness, T oldBusiness, String bizId,
        Collection<BusinessStorageFileField<T>> fields, String updateBy);

public <T> int markStorageFilesWaitDeleteByBiz(Object[] bizIds, Collection<BusinessStorageFileField<T>> fields,
        String updateBy);
```

旧的注解扫描 API 和 `@StorageFileField` 注解已删除，文件接入只保留显式映射方式

### 10. 下载入口只校验 token 和输出流

```java
/**
 * 写出文件内容
 * <p>
 * 该方法只负责统一校验访问 token、构造 FileInfo 并输出文件流，不判断具体业务菜单权限和数据权限
 * 普通业务私有文件的业务权限应在列表或详情接口里完成，文件 token 也只能由这些业务查询接口签发
 *
 * @param fileId 文件ID
 * @param thumbnail true 表示写出缩略图
 * @param attachment true 表示作为附件下载
 * @param response 响应对象
 */
private void writeStorageFile(Long fileId, boolean thumbnail, boolean attachment, String accessToken,
        HttpServletResponse response)
{
    SysStorageFile storageFile = storageFileService.selectStorageFileById(fileId);
    try
    {
        checkStorageDownloadAllowed(storageFile, null, null, accessToken);
        FileInfo fileInfo = buildDownloadFileInfo(storageFile, thumbnail);
        if (fileInfo == null)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String downloadName = StringUtils.isNotBlank(fileInfo.getOriginalFilename()) ? fileInfo.getOriginalFilename()
                : FileUtils.getName(fileInfo.getUrl());
        response.setContentType(StringUtils.isNotBlank(fileInfo.getContentType()) ? fileInfo.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        if (attachment)
        {
            // 下载入口设置附件响应头，预览入口则让浏览器按 Content-Type 尝试打开
            FileUtils.setAttachmentResponseHeader(response, downloadName);
        }
        response.getOutputStream().write(xFileStorageUtils.downloadBytes(fileInfo));
    }
    catch (ServiceException e)
    {
        log.warn("访问存储文件被拒绝，fileId={}，thumbnail={}，attachment={}，reason={}", fileId, thumbnail, attachment,
                e.getMessage());
        // ServiceException 可能只传业务提示不传错误码，默认按 403 访问拒绝处理
        Integer code = e.getCode();
        response.setStatus(code != null && code == HttpStatus.UNAUTHORIZED ? HttpServletResponse.SC_UNAUTHORIZED
                : HttpServletResponse.SC_FORBIDDEN);
    }
    catch (Exception e)
    {
        log.error("访问存储文件失败，fileId={}，thumbnail={}，attachment={}", fileId, thumbnail, attachment, e);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
}
```

## 五、为什么保留 bizType/bizId

业务表中的 `fileId/fileIds` 是主关联方式，适合表单保存、编辑回显和代码生成

文件表中的 `bizType/bizId` 是辅助管理字段，主要用于：

- 删除业务数据时批量标记该业务下的文件
- 按业务类型设置差异化清理或审计规则
- 文件后台管理按业务归类查询
- 避免只靠 URL 判断文件和业务关系

所以最终关系是：

```text
业务表 fileId/fileIds -> 精确找到文件
文件表 bizType/bizId -> 反向辅助管理文件生命周期
```

## 六、新业务接入规范

新业务建议按下面方式接入：

1. 业务表保留 `fileId` 或 `fileIds` 字段
2. Entity 只放数据库字段
3. 新增保存 DTO，接收业务字段和文件ID字段
4. 新增返回 VO，在 VO 中放 `fileList`
5. Service 中声明 `BusinessStorageFileField` 显式映射
6. 查询列表或详情时调用 `fillStorageFileList`
7. 新增成功后调用 `bindStorageFiles`
8. 修改成功后调用 `syncStorageFiles`
9. 删除成功后调用 `markStorageFilesWaitDeleteByBiz`

这套方式不再依赖实体注解和反射绑定，文件字段数量由业务 Service 自己声明，结构更直观

## 七、验证结果

已执行后端编译：

```text
mvn -pl ruoyi-admin -am -DskipTests "-Dmaven.compiler.useIncrementalCompilation=false" compile
```

编译结果：

```text
BUILD SUCCESS
```
