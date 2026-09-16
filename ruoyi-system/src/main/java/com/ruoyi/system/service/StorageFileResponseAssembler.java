package com.ruoyi.system.service;

import org.dromara.x.file.storage.core.FileInfo;
import org.springframework.stereotype.Component;
import com.ruoyi.common.core.domain.model.StorageFileUploadRespDTO;
import com.ruoyi.common.enums.StorageFileAccess;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.SysStorageFile;

/**
 * 存储文件响应组装器
 * <p>
 * 负责把 X File Storage 文件信息和文件资产记录转换为前端稳定使用的返回 DTO
 *
 * @author 955Kane
 */
@Component
public class StorageFileResponseAssembler
{
    /**
     * 文件对外访问地址构建器
     */
    private final StorageFileAccessUrlBuilder storageFileAccessUrlBuilder;

    /**
     * 存储文件短期访问授权服务
     */
    private final StorageFileAccessTokenService storageFileAccessTokenService;

    /**
     * 构造存储文件响应组装器
     *
     * @param storageFileAccessUrlBuilder 文件对外访问地址构建器
     * @param storageFileAccessTokenService 存储文件短期访问授权服务
     */
    public StorageFileResponseAssembler(StorageFileAccessUrlBuilder storageFileAccessUrlBuilder,
            StorageFileAccessTokenService storageFileAccessTokenService)
    {
        this.storageFileAccessUrlBuilder = storageFileAccessUrlBuilder;
        this.storageFileAccessTokenService = storageFileAccessTokenService;
    }

    /**
     * 根据上传结果和文件记录构建上传响应 DTO
     *
     * @param fileInfo X File Storage 文件信息
     * @param storageFile 数据库文件资产记录
     * @param uploadScene 上传场景编码
     * @param originalFilename 原始文件名兜底值
     * @return 上传响应 DTO
     */
    public StorageFileUploadRespDTO assemble(FileInfo fileInfo, SysStorageFile storageFile, String uploadScene,
            String originalFilename)
    {
        StorageFileUploadRespDTO resp = StorageFileUploadRespDTO.from(fileInfo, uploadScene, originalFilename);
        if (storageFile == null)
        {
            return resp;
        }
        // 上传成功后 FileRecorder 已经落库，这里用文件记录补齐 fileId、访问级别和对象 Key
        fillStorageFileFields(resp, storageFile);
        // 上传接口返回的私有文件仍处于临时态，需要签发绑定当前用户的短期 token 供保存前预览
        fillAccessUrlFields(resp, storageFile, true);
        return resp;
    }

    /**
     * 根据文件资产记录构建业务回显响应 DTO
     *
     * @param storageFile 数据库文件资产记录
     * @param bindCurrentUser true 表示私有文件 token 绑定当前登录用户
     * @return 文件回显响应 DTO
     */
    public StorageFileUploadRespDTO assemble(SysStorageFile storageFile, boolean bindCurrentUser)
    {
        StorageFileUploadRespDTO resp = new StorageFileUploadRespDTO();
        if (storageFile == null)
        {
            return resp;
        }
        // 文件表是业务回显的可信来源，直接从文件记录填充稳定字段
        fillStorageFileFields(resp, storageFile);
        resp.setNewFileName(storageFile.getFilename());
        resp.setOriginalFilename(storageFile.getOriginalFilename());
        resp.setPlatform(storageFile.getPlatform());
        resp.setUploadId(storageFile.getUploadId());
        resp.setUploadStatus(storageFile.getUploadStatus());
        resp.setThNewFileName(storageFile.getThFilename());
        resp.setSize(storageFile.getSize());
        resp.setThSize(storageFile.getThSize());
        resp.setContentType(storageFile.getContentType());
        fillAccessUrlFields(resp, storageFile, bindCurrentUser);
        return resp;
    }

    /**
     * 填充文件记录中的通用字段
     *
     * @param resp 上传响应 DTO
     * @param storageFile 数据库文件资产记录
     */
    private void fillStorageFileFields(StorageFileUploadRespDTO resp, SysStorageFile storageFile)
    {
        resp.setFileId(storageFile.getFileId());
        resp.setAccess(storageFile.getAccess());
        resp.setObjectKey(storageFile.getObjectKey());
        resp.setThObjectKey(storageFile.getThObjectKey());
    }

    /**
     * 填充前端可访问地址字段
     *
     * @param resp 上传响应 DTO
     * @param storageFile 数据库文件资产记录
     * @param bindCurrentUser true 表示私有文件 token 绑定当前登录用户
     */
    private void fillAccessUrlFields(StorageFileUploadRespDTO resp, SysStorageFile storageFile, boolean bindCurrentUser)
    {
        // previewUrl 用于图片、PDF 等文件展示，downloadUrl 用于附件下载
        String previewUrl = storageFileAccessUrlBuilder.buildPreviewUrl(storageFile);
        String downloadUrl = storageFileAccessUrlBuilder.buildDownloadUrl(storageFile);
        String thPreviewUrl = storageFileAccessUrlBuilder.buildThumbnailPreviewUrl(storageFile);
        if (StorageFileAccess.isPrivate(storageFile.getAccess()))
        {
            // 私有文件只返回后端代理地址，并追加短期访问 token
            String accessToken = bindCurrentUser ? storageFileAccessTokenService.issueUserBoundToken(storageFile.getFileId())
                    : storageFileAccessTokenService.issueToken(storageFile.getFileId());
            previewUrl = storageFileAccessTokenService.appendToken(previewUrl, accessToken);
            downloadUrl = storageFileAccessTokenService.appendToken(downloadUrl, accessToken);
            thPreviewUrl = storageFileAccessTokenService.appendToken(thPreviewUrl, accessToken);
        }
        resp.setPreviewUrl(previewUrl);
        resp.setDownloadUrl(downloadUrl);
        resp.setThPreviewUrl(thPreviewUrl);
        fillCompatibleUrlFields(resp, storageFile);
    }

    /**
     * 填充若依上传组件兼容字段
     *
     * @param resp 上传响应 DTO
     * @param storageFile 数据库文件资产记录
     */
    private void fillCompatibleUrlFields(StorageFileUploadRespDTO resp, SysStorageFile storageFile)
    {
        // 私有文件或后端代理模式下不能暴露真实存储 URL，兼容字段也使用代理预览地址
        if (!storageFileAccessUrlBuilder.allowExposeRealUrl(storageFile))
        {
            resp.setUrl(resp.getPreviewUrl());
            resp.setFileName(resp.getPreviewUrl());
            resp.setThUrl(resp.getThPreviewUrl());
            resp.setThFileName(resp.getThPreviewUrl());
            return;
        }
        // 公开直连模式允许返回真实地址，保持若依原上传组件的使用习惯
        resp.setUrl(resp.getPreviewUrl());
        resp.setFileName(resp.getPreviewUrl());
        resp.setThUrl(resp.getThPreviewUrl());
        resp.setThFileName(resp.getThPreviewUrl());
        if (StringUtils.isBlank(resp.getDownloadUrl()))
        {
            resp.setDownloadUrl(resp.getPreviewUrl());
        }
    }
}
