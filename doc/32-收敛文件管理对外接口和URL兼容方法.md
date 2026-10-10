# 收敛文件管理对外接口和URL兼容方法

## 改造目标

删除文件资产管理中不再作为标准链路使用的补查接口和 URL 下载入口，收敛脚手架对外暴露的文件访问方式。

本次改造后，普通业务私有文件回显统一走业务列表或详情接口，由业务层完成权限校验后调用 `businessStorageFileService.fillStorageFileList(...)` 填充文件列表；文件预览和下载继续走 fileId 代理入口。

## 改造前问题

改造前 `CommonController` 同时暴露了三类非核心接口：

```java
@PostMapping("/storage/download")
@Deprecated
public void storageDownload(@RequestParam("url") String url,
        @RequestParam(value = "bizType", required = false) String bizType,
        @RequestParam(value = "bizId", required = false) String bizId,
        @RequestParam(value = "token", required = false) String accessToken, HttpServletResponse response)
```

```java
@GetMapping("/file/info/{fileId}")
public AjaxResult getStorageFileInfo(@PathVariable Long fileId,
        @RequestParam(value = "token", required = false) String accessToken)
```

```java
@GetMapping("/file/listByIds")
public AjaxResult listStorageFileByIds(@RequestParam("fileIds") String fileIds,
        @RequestParam(value = "token", required = false) String accessToken)
```

这些接口不是若依原始接口，而是文件资产管理改造过程中新增的过渡能力。随着最终方案收敛到“业务接口返回 fileList”，这三个接口继续保留会带来两个问题：

1. `/common/storage/download` 鼓励前端继续按真实 URL 下载文件
2. `/common/file/info/{fileId}` 和 `/common/file/listByIds` 缺少业务上下文，容易被误用为普通业务私有文件回显入口

## 改造后方案

对外保留核心入口：

```text
/common/upload
/common/uploads
/common/file/preview/{fileId}
/common/file/download/{fileId}
```

普通业务私有文件标准链路：

```text
业务 Controller 完成 @PreAuthorize 权限校验
-> 业务 Service 查询业务数据
-> businessStorageFileService.fillStorageFileList(...)
-> 返回 file1List、file2List 等文件回显列表
-> 前端使用 previewUrl/downloadUrl 访问文件
```

内部继续保留 x-file-storage 必需的 URL 能力，例如 `getByUrl`、`delete(url)`、`selectStorageFileByUrl`。这些能力用于框架回调、上传后反查记录、头像旧文件兼容和补偿删除，不再作为业务绑定和对外下载主入口。

## 涉及文件

```text
ruoyi-admin/src/main/java/com/ruoyi/web/controller/common/CommonController.java
ruoyi-common/src/main/java/com/ruoyi/common/core/domain/model/StorageFileUploadRespDTO.java
ruoyi-system/src/main/java/com/ruoyi/system/service/ISysStorageFileService.java
ruoyi-system/src/main/java/com/ruoyi/system/service/impl/SysStorageFileServiceImpl.java
ruoyi-system/src/main/java/com/ruoyi/system/mapper/SysStorageFileMapper.java
ruoyi-system/src/main/resources/mapper/system/SysStorageFileMapper.xml
```

## 具体改动

### 1. 删除 URL 下载对外接口

原代码：

```java
/**
 * 下载 X File Storage 管理的文件
 * <p>
 * 私有文件不能只凭 URL 下载，通用入口只处理公开文件、本人临时文件、本人头像和管理员兜底下载
 *
 * @param url X File Storage 返回的文件 URL
 * @param bizType 可选业务类型，仅用于记录排查和识别误用入口
 * @param bizId 可选业务 ID，仅用于记录排查和识别误用入口
 * @param response 响应对象
 */
@PostMapping("/storage/download")
@Deprecated
public void storageDownload(@RequestParam("url") String url,
        @RequestParam(value = "bizType", required = false) String bizType,
        @RequestParam(value = "bizId", required = false) String bizId,
        @RequestParam(value = "token", required = false) String accessToken, HttpServletResponse response)
{
    // 兼容旧的 URL 下载入口，真实鉴权和响应流写出统一交给文件访问服务
    try
    {
        // 先查数据库文件记录，私有下载必须基于记录里的业务归属做鉴权
        SysStorageFile storageFile = storageFileService.selectStorageFileByUrl(url);
        checkStorageDownloadAllowed(storageFile, bizType, bizId, accessToken);
        // 数据库记录还原为 FileInfo，框架才能定位具体平台和对象路径
        FileInfo fileInfo = storageFileService.toFileInfo(storageFile);
        if (fileInfo == null)
        {
            throw new ServiceException("文件记录不存在");
        }
        // 原始文件名优先用于下载文件名，缺失时使用 URL 最后一段兜底
        String downloadName = StringUtils.isNotBlank(fileInfo.getOriginalFilename()) ? fileInfo.getOriginalFilename()
                : FileUtils.getName(fileInfo.getUrl());
        // Content-Type 有值时按真实类型返回，便于浏览器识别预览或下载
        response.setContentType(StringUtils.isNotBlank(fileInfo.getContentType()) ? fileInfo.getContentType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        // 设置附件响应头，中文文件名由 FileUtils 统一编码
        FileUtils.setAttachmentResponseHeader(response, downloadName);
        // 从实际存储平台下载文件内容并写入响应流
        response.getOutputStream().write(xFileStorageUtils.downloadBytes(fileInfo));
    }
    catch (ServiceException e)
    {
        // 权限或业务归属校验失败返回 403，避免私有文件 URL 泄露后被同系统用户下载
        log.warn("下载存储文件被拒绝，url={}，bizType={}，bizId={}，reason={}", url, bizType, bizId, e.getMessage());
        response.setStatus(HttpStatus.FORBIDDEN);
    }
    catch (Exception e)
    {
        // 下载异常写日志并返回 500，前端下载工具会按失败处理
        log.error("下载存储文件失败，url={}，bizType={}，bizId={}", url, bizType, bizId, e);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
}
```

现代码：

```java
/**
 * 通过文件ID预览文件
 * <p>
 * 公开文件允许匿名访问，私有文件必须携带登录令牌并通过通用下载规则
 *
 * @param fileId 文件ID
 * @param thumbnail true 表示预览缩略图
 * @param response 响应对象
 */
@GetMapping("/file/preview/{fileId}")
public void previewStorageFile(@PathVariable Long fileId,
        @RequestParam(value = "thumbnail", defaultValue = "false") Boolean thumbnail,
        @RequestParam(value = "token", required = false) String accessToken, HttpServletResponse response)
{
    // 公共预览入口统一通过 fileId 读取文件，私有业务文件必须带业务接口签发的短期 token
    writeStorageFile(fileId, Boolean.TRUE.equals(thumbnail), false, accessToken, response);
}
```

改动说明：删除 URL 下载入口后，外部下载只能通过 fileId 代理地址进入，避免业务继续把真实存储 URL 当作下载入参。

### 2. 删除文件信息补查接口

原代码：

```java
/**
 * 查询文件回显信息
 *
 * @param fileId 文件ID
 * @return 文件回显 DTO
 */
@GetMapping("/file/info/{fileId}")
public AjaxResult getStorageFileInfo(@PathVariable Long fileId,
        @RequestParam(value = "token", required = false) String accessToken)
{
    // 业务表只保存 fileId 时，前端回显需要通过文件表反查 originalFilename、previewUrl 和 downloadUrl
    SysStorageFile storageFile = storageFileService.selectStorageFileById(fileId);
    if (storageFile == null)
    {
        return AjaxResult.error("文件记录不存在");
    }
    checkStorageInfoAllowed(storageFile, accessToken);
    return AjaxResult.success(buildStorageFileRespDTO(storageFile));
}
```

现代码：该接口已删除

改动说明：普通业务文件回显不再由前端按 fileId 单独补查，而是由业务查询接口返回业务数据时一并返回 `fileList`。

### 3. 删除批量文件信息补查接口

原代码：

```java
/**
 * 批量查询文件回显信息
 *
 * @param fileIds 逗号分隔的文件ID，例如 70,73,74,75
 * @return 文件回显 DTO 列表
 */
@GetMapping("/file/listByIds")
public AjaxResult listStorageFileByIds(@RequestParam("fileIds") String fileIds,
        @RequestParam(value = "token", required = false) String accessToken)
{
    // 多文件字段保存逗号分隔 fileId 时，前端通过一次请求获取所有文件名和访问地址
    List<Long> idList = parseFileIds(fileIds);
    List<SysStorageFile> storageFiles = storageFileService.selectStorageFileByIds(idList);
    Map<Long, SysStorageFile> storageFileMap = new LinkedHashMap<Long, SysStorageFile>();
    for (SysStorageFile storageFile : storageFiles)
    {
        // 查询结果转成 Map，后续按入参顺序重新组装，保证前端回显顺序不乱
        storageFileMap.put(storageFile.getFileId(), storageFile);
    }
    List<StorageFileUploadRespDTO> fileList = new ArrayList<StorageFileUploadRespDTO>();
    for (Long fileId : idList)
    {
        SysStorageFile storageFile = storageFileMap.get(fileId);
        if (storageFile != null)
        {
            checkStorageInfoAllowed(storageFile, accessToken);
            fileList.add(buildStorageFileRespDTO(storageFile));
        }
    }
    return AjaxResult.success(fileList);
}
```

现代码：该接口已删除

改动说明：批量文件回显已由 `BusinessStorageFileServiceImpl` 在业务查询链路内完成，避免脱离业务上下文查询私有文件名和访问地址。

### 4. 删除 Controller 内补查专用私有方法

原代码：

```java
/**
 * 校验当前请求是否允许查看文件回显信息
 *
 * @param storageFile 存储文件记录
 * @param accessToken 文件短期访问 token
 */
private void checkStorageInfoAllowed(SysStorageFile storageFile, String accessToken)
{
    // 文件信息和文件内容使用同一套私有访问边界，避免任意登录用户按 fileId 枚举私有文件名
    checkStorageDownloadAllowed(storageFile, null, null, accessToken);
}
```

```java
/**
 * 解析逗号分隔的文件ID
 *
 * @param fileIds 逗号分隔的文件ID
 * @return 文件ID列表
 */
private List<Long> parseFileIds(String fileIds)
```

```java
/**
 * 根据文件记录构造前端回显 DTO
 *
 * @param storageFile 文件记录
 * @return 文件回显 DTO
 */
private StorageFileUploadRespDTO buildStorageFileRespDTO(SysStorageFile storageFile)
```

现代码：三个私有方法已删除

改动说明：这些方法只服务 `/file/info/{fileId}` 和 `/file/listByIds`，接口删除后不再有调用点。

### 5. 删除 URL 版业务绑定和同步服务方法

原代码：

```java
@Deprecated
public int bindStorageFile(String url, String bizType, String bizId, String updateBy);

@Deprecated
public int bindStorageFiles(Collection<String> urls, String bizType, String bizId, String updateBy);

@Deprecated
public int syncBizStorageFiles(Collection<String> newUrls, Collection<String> oldUrls, String bizType, String bizId,
        String updateBy);

@Deprecated
public FileInfo getBusinessFileInfo(String url, String bizType, String bizId);
```

现代码：

```java
/**
 * 根据文件ID集合批量绑定文件到业务
 *
 * @param fileIds 文件ID集合
 * @param bizType 业务类型
 * @param bizId 业务ID
 * @param updateBy 更新者
 * @return 影响行数
 */
public int bindStorageFilesByIds(Collection<Long> fileIds, String bizType, String bizId, String updateBy);

/**
 * 根据文件ID集合同步业务文件集合
 * <p>
 * 新文件会绑定到当前业务，旧文件中不再出现的文件会标记待删除
 *
 * @param newFileIds 当前业务保存后的文件ID集合
 * @param oldFileIds 修改前业务已关联的文件ID集合
 * @param bizType 业务类型
 * @param bizId 业务ID
 * @param updateBy 更新者
 * @return 影响行数
 */
public int syncBizStorageFilesByIds(Collection<Long> newFileIds, Collection<Long> oldFileIds, String bizType,
        String bizId, String updateBy);
```

改动说明：新业务只保留 fileId 版绑定和同步方法，避免 URL 版方法继续成为业务接入入口。

### 6. 删除 URL 批量绑定 Mapper 和 SQL

原代码：

```java
public int bindStorageFile(@Param("urlHash") String urlHash, @Param("url") String url, @Param("bizType") String bizType,
        @Param("bizId") String bizId, @Param("updateBy") String updateBy);

public int bindStorageFiles(@Param("files") List<SysStorageFile> files, @Param("bizType") String bizType,
        @Param("bizId") String bizId, @Param("updateBy") String updateBy);
```

```xml
<update id="bindStorageFile">
    update sys_storage_file
    set file_status = '1',
        biz_type = #{bizType},
        biz_id = #{bizId},
        update_by = #{updateBy},
        update_time = sysdate()
    where url_hash = #{urlHash}
      and url = #{url}
      and file_status != '3'
</update>

<update id="bindStorageFiles">
    update sys_storage_file
    set file_status = '1',
        biz_type = #{bizType},
        biz_id = #{bizId},
        update_by = #{updateBy},
        update_time = sysdate()
    where file_status != '3'
      and
      <foreach collection="files" item="file" open="(" separator="or" close=")">
          (url_hash = #{file.urlHash} and url = #{file.url})
      </foreach>
</update>
```

现代码：

```xml
<update id="bindStorageFilesByIds">
    update sys_storage_file
    set file_status = #{usedStatus,jdbcType=VARCHAR},
        biz_type = #{bizType},
        biz_id = #{bizId},
        update_by = #{updateBy},
        update_time = sysdate()
    where file_status != #{deletedStatus}
      and file_id in
      <foreach collection="fileIds" item="fileId" open="(" separator="," close=")">
          #{fileId}
      </foreach>
      and (
          (file_status = #{tempStatus} and create_by = #{createBy})
          or (biz_type = #{bizType} and biz_id = #{bizId})
      )
</update>
```

改动说明：Mapper 层只保留 fileId 版绑定 SQL，并继续保留“只能绑定本人临时文件或当前业务已绑定文件”的防误绑条件。

### 7. 清理 DTO 重复注释

原代码：

```java
/**
 * 访问级别，public 表示公开访问，private 表示需要后端鉴权下载
 * 访问级别，public 表示公开访问，private 表示需要后端鉴权访问
 */
private String access;
```

现代码：

```java
/**
 * 访问级别，public 表示公开访问，private 表示需要后端鉴权访问
 */
private String access;
```

改动说明：移除重复注释，保持 DTO 字段说明清晰。

## 验证结果

## 前端同步调整

### 1. 上传组件不再调用通用 fileId 补查接口

原代码：

```js
// 省略
function getFileInfoMap() {
    const providedFileInfoMap = getProvidedFileInfoMap();
    const missingFileIds = list.filter(item => isFileIdValue(item) && !providedFileInfoMap[item]);
    if (!missingFileIds.length) {
        return providedFileInfoMap;
    }
    const response = await request({
        url: "/common/file/listByIds",
        method: "get",
        params: { fileIds: missingFileIds.join(",") }
    });
    return mergeFileInfoMap(providedFileInfoMap, response.data || []);
}
// 省略
```

现代码：

```js
// 省略
// 获取业务接口已经返回的文件回显信息
function getFileInfoMap() {
  // 普通业务私有文件必须由业务接口先完成权限校验，再把授权文件列表传给组件
  return getProvidedFileInfoMap();
}

// 将业务接口返回的授权文件列表转成 Map，供 v-model 中的 fileId 回显使用
function getProvidedFileInfoMap() {
  return (props.storageFileList || []).reduce((map, fileInfo) => {
    if (fileInfo && fileInfo.fileId) {
      map[String(fileInfo.fileId)] = fileInfo;
    }
    return map;
  }, {});
}
// 省略
```

改动说明：`FileUpload` 和 `ImageUpload` 都改为只消费 `storageFileList`，不再从组件内部调用 `/common/file/listByIds`。这样普通私有文件的文件名、预览地址、下载地址都必须来自业务接口，避免组件绕过业务查询权限和业务数据权限单独枚举文件。

### 2. 点位列表使用业务接口返回的文件列表回显

原代码：

```js
// 省略
async function loadNodeStorageFileDisplay(rows) {
  const fileIds = collectNodeFileIds(rows)
  if (!fileIds.length) {
    return
  }
  const fileInfoMap = await getStorageFileInfoMap(fileIds)
  rows.forEach(row => {
    row.file1FileList = buildStorageFileList(row.file1, fileInfoMap)
    row.file1PreviewUrl = row.file1FileList[0]?.thPreviewUrl || row.file1FileList[0]?.previewUrl || ""
  })
}
// 省略
```

现代码：

```js
// 省略
/** 加载点位列表文件展示信息 */
function loadNodeStorageFileDisplay(rows) {
  const fileInfoMap = collectProvidedStorageFileMap(rows)
  if (!Object.keys(fileInfoMap).length) {
    return
  }
  rows.forEach(row => {
    // 图片列使用缩略图预览地址，避免列表里继续把 fileId 当图片地址使用
    row.file1FileList = buildStorageFileList(row.file1, fileInfoMap)
    row.file1PreviewUrl = row.file1FileList[0]?.thPreviewUrl || row.file1FileList[0]?.previewUrl || ""
    // 附件、富文本、音频、视频列展示 originalFilename，而不是展示业务表保存的 fileId
    row.file2FileList = buildStorageFileList(row.file2, fileInfoMap)
    row.file3FileList = buildStorageFileList(row.file3, fileInfoMap)
    row.file4FileList = buildStorageFileList(row.file4, fileInfoMap)
    row.file5FileList = buildStorageFileList(row.file5, fileInfoMap)
  })
}

/** 收集业务列表接口已经返回的授权文件信息 */
function collectProvidedStorageFileMap(rows) {
  const fileInfoMap = {}
  rows.forEach(row => {
    ;[row.file1List, row.file2List, row.file3List, row.file4List, row.file5List].forEach(fileList => {
      ;(fileList || []).forEach(fileInfo => {
        if (fileInfo && fileInfo.fileId) {
          fileInfoMap[String(fileInfo.fileId)] = fileInfo
        }
      })
    })
  })
  return fileInfoMap
}
// 省略
```

改动说明：点位管理不再按 `fileId` 主动补查通用文件接口，而是使用 `NodeController.list` / `NodeController.getInfo` 返回的 `file1List`、`file2List`、`file3List`、`file4List`、`file5List` 做回显。业务接口已经带有若依权限注解，文件回显跟业务查询链路保持一致。

### 3. 删除接口后的替代链路

```text
点位列表/详情接口
  -> 若依 @PreAuthorize 校验菜单权限
  -> 业务 Service 查询点位数据
  -> businessStorageFileService.fillStorageFileList 填充授权文件 DTO
  -> 前端 ImageUpload/FileUpload 通过 storageFileList 回显
```

改动说明：删除 `/common/file/listByIds` 后，不再提供“任意页面拿 fileId 直接查文件信息”的通用入口。需要回显业务私有文件时，由对应业务查询接口返回文件 DTO；需要下载时，继续使用 DTO 中的 `downloadUrl` 访问后端代理下载接口。

执行后端编译：

```text
mvn -pl ruoyi-admin -am -DskipTests "-Dmaven.compiler.useIncrementalCompilation=false" compile
```

结果：

```text
BUILD SUCCESS
```
