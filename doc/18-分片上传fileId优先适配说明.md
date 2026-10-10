# 分片上传 fileId 优先适配说明

## 1. 改造背景
文件上传模块已经支持公开文件、私有文件、后端代理地址和短期访问 token

普通上传、批量上传和分片初始化接口返回给前端的 `url` 字段，可能不再是对象存储返回的真实 URL，而是后端代理地址，例如：

```text
/common/file/preview/89?token=xxx
```

但是 X File Storage 的分片后续操作需要依赖真实文件记录中的平台、路径、文件名和 `uploadId`

如果后续分片接口继续只接收 `url`，就可能把代理 URL 传给：

```java
fileStorageService.getFileInfoByUrl(url)
```

这会导致框架无法从 `sys_storage_file` 正确还原 `FileInfo`，从而影响上传分片、查询分片、合并分片和取消分片

## 2. 改造前的问题
### 2.1 后续分片接口只按 url 定位
原代码：

```java
// 省略
@PostMapping("/multipart/uploadPart")
public AjaxResult uploadPart(@RequestParam("url") String url, @RequestParam("partNumber") Integer partNumber,
        @RequestParam("file") MultipartFile file)
{
    try
    {
        // 分片上传成功后框架会回调 FileRecorder.saveFilePart 保存分片记录
        storageFileService.checkMultipartOwner(url);
        FilePartInfo filePartInfo = xFileStorageUtils.uploadPart(url, partNumber, file);
        return AjaxResult.success(StorageFilePartRespDTO.from(filePartInfo));
    }
    catch (Exception e)
    {
        // 分片上传失败时记录 url 和分片编号，方便前端重试指定分片
        log.error("上传文件分片失败，url={}，partNumber={}，size={}", url, partNumber,
                file == null ? null : file.getSize(), e);
        return AjaxResult.error(e.getMessage());
    }
}
// 省略
```

问题：

```text
url 可能是代理地址，不一定是数据库保存的真实文件 URL
私有文件 url 可能携带 token，token 变化后也不适合作为分片会话定位条件
访问域名后续调整时，前端持有的 url 可能和 sys_storage_file.url 不一致
```

### 2.2 工具类只提供 url 版本分片操作
原代码：

```java
// 省略
public FilePartInfo uploadPart(String url, Integer partNumber, MultipartFile file)
{
    // 分片编号从 1 开始，非法编号直接拒绝
    if (partNumber == null || partNumber <= 0)
    {
        throw new IllegalArgumentException("分片编号必须大于 0");
    }
    if (file == null || file.isEmpty())
    {
        throw new IllegalArgumentException("分片文件不能为空");
    }
    // 从数据库记录按 URL 还原 FileInfo，里面包含平台、路径和 uploadId
    FileInfo fileInfo = getRequiredFileInfo(url);
    // 上传当前分片，框架成功后会通过 FileRecorder.saveFilePart 保存分片记录
    return fileStorageService.uploadPart(fileInfo, partNumber, file, file.getSize()).upload();
}
// 省略
```

问题：

```text
调用方没有办法直接传已经从 fileId 查询出来的 FileInfo
所有分片后续操作都被迫重新按 url 查询
```

## 3. 改造后方案
### 3.1 分片后续接口优先使用 fileId
现代码：

```java
// 省略
@PostMapping("/multipart/uploadPart")
public AjaxResult uploadPart(@RequestParam(value = "fileId", required = false) Long fileId,
        @RequestParam(value = "url", required = false) String url, @RequestParam("partNumber") Integer partNumber,
        @RequestParam("file") MultipartFile file)
{
    try
    {
        // 分片上传成功后框架会回调 FileRecorder.saveFilePart 保存分片记录
        checkMultipartOwner(fileId, url);
        FilePartInfo filePartInfo = xFileStorageUtils.uploadPart(getMultipartFileInfo(fileId, url), partNumber, file);
        return AjaxResult.success(StorageFilePartRespDTO.from(filePartInfo));
    }
    catch (Exception e)
    {
        // 分片上传失败时记录 url 和分片编号，方便前端重试指定分片
        log.error("上传文件分片失败，url={}，partNumber={}，size={}", url, partNumber,
                file == null ? null : file.getSize(), e);
        return AjaxResult.error(e.getMessage());
    }
}
// 省略
```

改动说明：

```text
新增 fileId 参数，并设置为非必填
fileId 不为空时，优先通过 fileId 查询 sys_storage_file
url 参数继续保留，只作为旧前端兼容
```

### 3.2 listParts、complete、abort 同步改为 fileId 优先
现代码：

```java
// 省略
@GetMapping("/multipart/listParts")
public AjaxResult listParts(@RequestParam(value = "fileId", required = false) Long fileId,
        @RequestParam(value = "url", required = false) String url,
        @RequestParam(value = "maxParts", required = false) Integer maxParts,
        @RequestParam(value = "partNumberMarker", required = false) Integer partNumberMarker)
{
    try
    {
        // 部分平台不支持列举分片，具体能力可先调用 support 接口确认
        checkMultipartOwner(fileId, url);
        FilePartInfoList filePartInfoList = xFileStorageUtils.listParts(getMultipartFileInfo(fileId, url), maxParts,
                partNumberMarker);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("file", buildUploadRespDTO(filePartInfoList.getFileInfo(), null, null));
        ajax.put("partList", toFilePartList(filePartInfoList.getList()));
        ajax.put("maxParts", filePartInfoList.getMaxParts());
        ajax.put("truncated", filePartInfoList.getIsTruncated());
        ajax.put("partNumberMarker", filePartInfoList.getPartNumberMarker());
        ajax.put("nextPartNumberMarker", filePartInfoList.getNextPartNumberMarker());
        return ajax;
    }
    catch (Exception e)
    {
        // 查询分片失败时记录分页参数，便于判断是否平台不支持 listParts
        log.error("查询文件分片失败，url={}，maxParts={}，partNumberMarker={}", url, maxParts, partNumberMarker, e);
        return AjaxResult.error(e.getMessage());
    }
}

@PostMapping("/multipart/complete")
public AjaxResult completeMultipartUpload(@RequestParam(value = "fileId", required = false) Long fileId,
        @RequestParam(value = "url", required = false) String url)
{
    try
    {
        // 合并成功后框架会更新主文件记录并删除分片记录
        checkMultipartOwner(fileId, url);
        FileInfo fileInfo = xFileStorageUtils.completeMultipartUpload(getMultipartFileInfo(fileId, url));
        return AjaxResult.success(buildUploadRespDTO(fileInfo, null, null));
    }
    catch (Exception e)
    {
        // 合并失败时记录 url，便于根据 uploadId 和平台日志继续排查
        log.error("完成分片上传失败，url={}", url, e);
        return AjaxResult.error(e.getMessage());
    }
}

@PostMapping("/multipart/abort")
public AjaxResult abortMultipartUpload(@RequestParam(value = "fileId", required = false) Long fileId,
        @RequestParam(value = "url", required = false) String url)
{
    try
    {
        // 取消成功后框架会通知平台释放未合并分片
        checkMultipartOwner(fileId, url);
        FileInfo fileInfo = xFileStorageUtils.abortMultipartUpload(getMultipartFileInfo(fileId, url));
        return AjaxResult.success(buildUploadRespDTO(fileInfo, null, null));
    }
    catch (Exception e)
    {
        // 取消失败时记录 url，便于后续人工清理未完成的分片会话
        log.error("取消分片上传失败，url={}", url, e);
        return AjaxResult.error(e.getMessage());
    }
}
// 省略
```

改动说明：

```text
四个分片后续接口统一 fileId 优先
前端新版应传 fileId
旧前端仍可传 url
```

## 4. 新增 Controller 辅助方法
现代码：

```java
// 省略
/**
 * 获取分片上传会话文件信息
 *
 * @param fileId 文件ID，优先使用
 * @param url 文件真实地址，仅兼容旧前端
 * @return X File Storage 文件信息
 */
private FileInfo getMultipartFileInfo(Long fileId, String url)
{
    // fileId 不受代理 URL、临时 token 或访问域名变更影响，作为分片后续操作的主定位条件
    if (fileId != null)
    {
        SysStorageFile storageFile = storageFileService.selectStorageFileById(fileId);
        if (storageFile == null)
        {
            throw new ServiceException("文件记录不存在");
        }
        FileInfo fileInfo = storageFileService.toFileInfo(storageFile);
        if (fileInfo == null)
        {
            throw new ServiceException("文件记录不存在");
        }
        return fileInfo;
    }
    // 旧前端只传 url 时仍允许按 URL 还原 FileInfo
    if (StringUtils.isBlank(url))
    {
        throw new ServiceException("文件ID不能为空");
    }
    return xFileStorageUtils.getFileInfoByUrl(url);
}

/**
 * 校验分片上传会话归属
 *
 * @param fileId 文件ID，优先使用
 * @param url 文件真实地址，仅兼容旧前端
 */
private void checkMultipartOwner(Long fileId, String url)
{
    // 新前端按 fileId 校验，避免代理 URL 无法反查真实上传记录
    if (fileId != null)
    {
        storageFileService.checkMultipartOwner(fileId);
        return;
    }
    // 旧前端保留 URL 校验能力
    storageFileService.checkMultipartOwner(url);
}
// 省略
```

改动说明：

```text
getMultipartFileInfo 负责定位真实 FileInfo
checkMultipartOwner 负责复用分片任务归属校验
Controller 不直接拼接 FileInfo，避免分散平台、路径、uploadId 的还原逻辑
```

## 5. XFileStorageUtils 增加 FileInfo 重载
### 5.1 上传分片
现代码：

```java
// 省略
public FilePartInfo uploadPart(String url, Integer partNumber, MultipartFile file)
{
    // URL 版本保留给旧前端，内部统一转为 FileInfo 版本
    return uploadPart(getRequiredFileInfo(url), partNumber, file);
}

/**
 * 上传大文件分片
 *
 * @param fileInfo 分片上传文件信息
 * @param partNumber 分片编号，从 1 开始
 * @param file 当前分片文件
 * @return 已上传的分片信息
 */
public FilePartInfo uploadPart(FileInfo fileInfo, Integer partNumber, MultipartFile file)
{
    // 分片编号从 1 开始，非法编号直接拒绝
    if (partNumber == null || partNumber <= 0)
    {
        throw new IllegalArgumentException("分片编号必须大于 0");
    }
    if (file == null || file.isEmpty())
    {
        throw new IllegalArgumentException("分片文件不能为空");
    }
    // FileInfo 里面包含平台、路径和 uploadId，fileId 调用方可以避免代理 URL 反查失败
    FileInfo requiredFileInfo = getRequiredFileInfo(fileInfo);
    // 上传当前分片，框架成功后会通过 FileRecorder.saveFilePart 保存分片记录
    return fileStorageService.uploadPart(requiredFileInfo, partNumber, file, file.getSize()).upload();
}
// 省略
```

### 5.2 查询分片
现代码：

```java
// 省略
public FilePartInfoList listParts(String url, Integer maxParts, Integer partNumberMarker)
{
    // URL 版本保留给旧前端，内部统一转为 FileInfo 版本
    return listParts(getRequiredFileInfo(url), maxParts, partNumberMarker);
}

/**
 * 查询已经上传的分片列表
 *
 * @param fileInfo 分片上传文件信息
 * @param maxParts 最多返回的分片数量
 * @param partNumberMarker 分页起始分片编号
 * @return 分片列表
 */
public FilePartInfoList listParts(FileInfo fileInfo, Integer maxParts, Integer partNumberMarker)
{
    // FileInfo 里面包含平台、路径和 uploadId，fileId 调用方可以避免代理 URL 反查失败
    FileInfo requiredFileInfo = getRequiredFileInfo(fileInfo);
    // 构造列举请求，由框架根据平台能力决定是否支持
    ListPartsPretreatment listParts = fileStorageService.listParts(requiredFileInfo);
    if (maxParts != null && maxParts > 0)
    {
        // 设置本次最多返回的分片数量
        listParts.setMaxParts(maxParts);
    }
    if (partNumberMarker != null && partNumberMarker > 0)
    {
        // 设置分页起始分片编号
        listParts.setPartNumberMarker(partNumberMarker);
    }
    return listParts.listParts();
}
// 省略
```

### 5.3 完成和取消分片
现代码：

```java
// 省略
public FileInfo completeMultipartUpload(String url)
{
    // URL 版本保留给旧前端，内部统一转为 FileInfo 版本
    return completeMultipartUpload(getRequiredFileInfo(url));
}

/**
 * 完成手动分片上传
 *
 * @param fileInfo 分片上传文件信息
 * @return 合并后的文件信息
 */
public FileInfo completeMultipartUpload(FileInfo fileInfo)
{
    // FileInfo 里面包含平台、路径和 uploadId，fileId 调用方可以避免代理 URL 反查失败
    FileInfo requiredFileInfo = getRequiredFileInfo(fileInfo);
    // 通知存储平台合并分片，成功后框架会更新主文件记录并清理分片记录
    return fileStorageService.completeMultipartUpload(requiredFileInfo).complete();
}

public FileInfo abortMultipartUpload(String url)
{
    // URL 版本保留给旧前端，内部统一转为 FileInfo 版本
    return abortMultipartUpload(getRequiredFileInfo(url));
}

/**
 * 取消手动分片上传
 *
 * @param fileInfo 分片上传文件信息
 * @return 取消后的文件信息
 */
public FileInfo abortMultipartUpload(FileInfo fileInfo)
{
    // FileInfo 里面包含平台、路径和 uploadId，fileId 调用方可以避免代理 URL 反查失败
    FileInfo requiredFileInfo = getRequiredFileInfo(fileInfo);
    // 通知存储平台取消分片上传，成功后框架会清理分片记录
    return fileStorageService.abortMultipartUpload(requiredFileInfo).abort();
}
// 省略
```

### 5.4 FileInfo 分片会话校验
现代码：

```java
// 省略
/**
 * 获取必须存在且带 uploadId 的分片文件信息
 *
 * @param fileInfo 文件信息
 * @return 文件信息
 */
private FileInfo getRequiredFileInfo(FileInfo fileInfo)
{
    // fileId 链路已经在业务层查出 FileInfo，这里只做分片会话必要字段校验
    if (fileInfo == null)
    {
        throw new IllegalArgumentException("文件记录不存在");
    }
    if (StringUtils.isBlank(fileInfo.getUploadId()))
    {
        throw new IllegalArgumentException("文件不是有效的分片上传会话");
    }
    return fileInfo;
}
// 省略
```

改动说明：

```text
URL 版本继续存在，兼容旧调用
FileInfo 版本作为新主链路，避免代理 URL 反查失败
新增 FileInfo 校验方法，确保后续操作必须是有效分片会话
```

## 6. Service 增加 fileId 归属校验
### 6.1 接口层
现代码：

```java
// 省略
/**
 * 校验分片上传会话是否属于当前登录用户
 *
 * @param fileId 文件ID
 */
public void checkMultipartOwner(Long fileId);

/**
 * 校验分片上传会话是否属于当前登录用户
 *
 * @param url 文件访问地址
 */
public void checkMultipartOwner(String url);
// 省略
```

### 6.2 实现层
现代码：

```java
// 省略
@Override
public void checkMultipartOwner(Long fileId)
{
    // fileId 是分片后续操作的推荐定位条件，不受代理 URL 和访问域名变化影响
    checkMultipartOwner(selectStorageFileById(fileId));
}

@Override
public void checkMultipartOwner(String url)
{
    // URL 版本保留给旧前端和旧调用方兼容使用
    checkMultipartOwner(selectStorageFileByUrl(url));
}

/**
 * 校验分片上传会话是否属于当前登录用户
 *
 * @param storageFile 存储文件记录
 */
private void checkMultipartOwner(SysStorageFile storageFile)
{
    if (storageFile == null)
    {
        throw new ServiceException("文件记录不存在");
    }
    if (StringUtils.isBlank(storageFile.getUploadId()))
    {
        throw new ServiceException("文件不是有效的分片上传会话");
    }
    // 超级管理员允许兜底处理异常分片任务，普通用户只能操作自己初始化的分片上传
    if (SecurityUtils.isAdmin())
    {
        return;
    }
    String username = SecurityUtils.getUsername();
    if (!username.equals(storageFile.getCreateBy()))
    {
        throw new ServiceException("只能操作自己创建的分片上传任务");
    }
}
// 省略
```

改动说明：

```text
新增 fileId 版校验入口
URL 版仍然保留兼容
实际校验逻辑统一收敛到 private checkMultipartOwner(SysStorageFile storageFile)
避免 fileId 版和 URL 版未来出现规则不一致
```

## 7. 改造后链路
### 7.1 新前端推荐链路
```text
1. 前端调用 /common/multipart/init
2. 后端创建分片上传会话，并写入 sys_storage_file
3. 返回 fileId、uploadId、url 等信息
4. 前端后续调用 uploadPart/listParts/complete/abort 时传 fileId
5. 后端按 fileId 查询 sys_storage_file
6. 后端将 SysStorageFile 转为 FileInfo
7. XFileStorageUtils 使用 FileInfo 调用 X File Storage 分片能力
```

### 7.2 旧前端兼容链路
```text
1. 旧前端继续传 url
2. 后端按 url 查询 sys_storage_file
3. 找到 FileInfo 后继续执行分片操作
```

旧链路只适合真实 URL 未被代理替换的场景，新业务应优先使用 `fileId`

## 8. 接口调用示例
### 8.1 上传分片
```http
POST /common/multipart/uploadPart
Content-Type: multipart/form-data

fileId=89
partNumber=1
file=@part-1.bin
```

### 8.2 查询分片
```http
GET /common/multipart/listParts?fileId=89&maxParts=100
```

### 8.3 完成分片
```http
POST /common/multipart/complete
Content-Type: application/x-www-form-urlencoded

fileId=89
```

### 8.4 取消分片
```http
POST /common/multipart/abort
Content-Type: application/x-www-form-urlencoded

fileId=89
```

## 9. 本次改造结论
这次改造没有推翻原有分片上传能力，只是把后续分片操作的定位条件从“只依赖 URL”调整为“优先 fileId，兼容 URL”

这样更适合当前脚手架最终方案：

```text
业务表保存 fileId/fileIds
文件资产表保存真实 URL、url_hash、objectKey、uploadId
前端展示和访问可以使用代理 URL
后端内部操作真实文件时通过 fileId 还原 FileInfo
```
