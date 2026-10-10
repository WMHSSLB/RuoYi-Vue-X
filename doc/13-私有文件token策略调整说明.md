# 私有文件 token 策略调整说明

## 1. 调整目标

后台管理系统中的私有业务文件默认不再使用可转发的分享式 token，而是使用绑定当前登录用户的 token

最终默认规则为：

```text
私有业务文件 = fileId + 文件 token + 当前登录 JWT
```

业务接口完成菜单权限和数据权限校验后，为当前登录用户签发文件 token

用户 A 拿到的下载地址复制给用户 B 后，用户 B 即使登录也不能下载，因为 token 中绑定的是用户 A 的 userId

## 2. 默认业务私有文件

业务接口继续调用：

```java
storageFileService.buildAuthorizedFileListByIds(fileIds);
```

该方法现在默认签发用户绑定 token

适用场景：

- 后台管理系统业务附件
- 合同、证件、单据、点位附件等跟业务数据权限绑定的文件
- 不希望复制链接后其他用户可访问的私有文件

## 3. 短期分享式 token

短期分享式 token 仍然保留，但需要业务显式调用：

```java
storageFileService.buildShareAuthorizedFileListByIds(fileIds);
```

分享式 token 只绑定 fileId，不绑定 userId

只要 token 未过期，拿到链接的人就可以访问

适用场景：

- 明确需要临时分享链接
- 外部协作下载
- 非敏感临时文件分享

## 4. 通用下载入口

业务页面使用：

```text
GET /common/file/preview/{fileId}?token=xxx
GET /common/file/download/{fileId}?token=xxx
```

普通业务私有文件没有有效 token 时直接拒绝

管理员不再通过 `/common/file/download/{fileId}` 绕过业务私有文件 token

## 5. 文件管理后台入口

管理员下载文件走：

```text
GET /system/storage/file/download/{fileId}
```

该入口由权限 `system:storage:download` 控制

它用于文件管理员排查、管理和下载文件，不混入普通业务下载链路

## 6. 本次涉及代码

`ISysStorageFileService` 新增分享式授权方法：

```java
public List<StorageFileUploadRespDTO> buildShareAuthorizedFileListByIds(Collection<Long> fileIds);
```

`SysStorageFileServiceImpl` 默认业务授权改为用户绑定 token：

```java
@Override
public List<StorageFileUploadRespDTO> buildAuthorizedFileListByIds(Collection<Long> fileIds)
{
    // 业务私有文件默认签发绑定当前登录用户的 token，避免授权链接被复制给其他账号继续访问
    return buildAuthorizedFileListByIds(fileIds, true);
}
```

`SysStorageFileServiceImpl` 保留分享式 token：

```java
@Override
public List<StorageFileUploadRespDTO> buildShareAuthorizedFileListByIds(Collection<Long> fileIds)
{
    // 分享式 token 只绑定 fileId，不绑定当前用户，仅供明确需要短期分享链接的业务主动调用
    return buildAuthorizedFileListByIds(fileIds, false);
}
```

`CommonController` 上传私有临时文件时签发用户绑定 token：

```java
String accessToken = storageFileAccessTokenService.issueUserBoundToken(storageFile.getFileId());
```

`CommonController` 通用下载入口不再让管理员绕过业务私有文件 token：

```java
if (StringUtils.isNotBlank(storageFile.getBizType()) && !"avatar".equals(storageFile.getBizType()))
{
    throw new ServiceException("业务私有文件缺少访问授权");
}

// 超级管理员和拥有统一下载权限的用户只兜底处理非业务绑定文件，业务私有文件请走业务接口签发 token
if (hasStorageDownloadPermission())
{
    return;
}
```
