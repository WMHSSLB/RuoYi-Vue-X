package com.ruoyi.common.core.domain.model;

import org.dromara.x.file.storage.core.FileInfo;
import com.ruoyi.common.constant.StorageFileConstants;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;

/**
 * 存储文件上传返回 DTO
 * <p>
 * 用于统一普通上传、批量上传和分片合并后的返回字段，避免控制器里反复手写 Map
 *
 * @author 955Kane
 */
public class StorageFileUploadRespDTO
{
    /**
     * 文件资产ID
     */
    private Long fileId;

    /**
     * 原图对外访问地址
     */
    private String url;

    /**
     * 原图预览地址
     */
    private String previewUrl;

    /**
     * 原图下载地址
     */
    private String downloadUrl;

    /**
     * 原图对象Key
     */
    private String objectKey;

    /**
     * 兼容若依上传组件的文件地址字段
     */
    private String fileName;

    /**
     * 保存后的文件名
     */
    private String newFileName;

    /**
     * 上传时的原始文件名
     */
    private String originalFilename;

    /**
     * 实际使用的存储平台
     */
    private String platform;

    /**
     * 上传场景编码
     */
    private String uploadScene;

    /**
     * 分片上传会话 ID
     */
    private String uploadId;

    /**
     * 分片上传状态
     */
    private Integer uploadStatus;

    /**
     * 缩略图对外访问地址
     */
    private String thUrl;

    /**
     * 缩略图预览地址
     */
    private String thPreviewUrl;

    /**
     * 缩略图对象Key
     */
    private String thObjectKey;

    /**
     * 兼容若依上传组件的缩略图地址字段
     */
    private String thFileName;

    /**
     * 缩略图文件名
     */
    private String thNewFileName;

    /**
     * 原图大小，单位字节
     */
    private Long size;

    /**
     * 缩略图大小，单位字节
     */
    private Long thSize;

    /**
     * 文件内容类型
     */
    private String contentType;

    /**
     * 访问级别，public 表示公开访问，private 表示需要后端鉴权访问
     */
    private String access;

    /**
     * 根据 X File Storage 文件信息构造上传返回 DTO
     *
     * @param fileInfo X File Storage 文件信息
     * @param uploadScene 上传场景编码
     * @param fallbackOriginalFilename 原始文件名兜底值
     * @return 上传返回 DTO
     */
    public static StorageFileUploadRespDTO from(FileInfo fileInfo, String uploadScene, String fallbackOriginalFilename)
    {
        StorageFileUploadRespDTO resp = new StorageFileUploadRespDTO();
        if (fileInfo == null)
        {
            return resp;
        }
        // 这里先保存框架返回的真实 URL，控制器会根据访问级别转换成对外地址
        String url = fileInfo.getUrl();
        resp.setUrl(url);
        resp.setFileName(url);
        resp.setNewFileName(FileUtils.getName(url));
        // 原始文件名优先使用框架记录，缺失时使用调用方传入的文件名
        resp.setOriginalFilename(StringUtils.isNotBlank(fileInfo.getOriginalFilename()) ? fileInfo.getOriginalFilename()
                : fallbackOriginalFilename);
        resp.setPlatform(fileInfo.getPlatform());
        resp.setUploadScene(uploadScene);
        resp.setUploadId(fileInfo.getUploadId());
        resp.setUploadStatus(fileInfo.getUploadStatus());
        resp.setThUrl(fileInfo.getThUrl());
        resp.setThFileName(fileInfo.getThUrl());
        resp.setThNewFileName(FileUtils.getName(fileInfo.getThUrl()));
        resp.setSize(fileInfo.getSize());
        resp.setThSize(fileInfo.getThSize());
        resp.setContentType(fileInfo.getContentType());
        resp.setAccess(resolveAccess(fileInfo));
        return resp;
    }

    /**
     * 根据文件路径推断访问级别
     *
     * @param fileInfo X File Storage 文件信息
     * @return public、private 或 null
     */
    private static String resolveAccess(FileInfo fileInfo)
    {
        // 当前路径规则由 XFileStorageUtils 统一生成，第一层目录就是访问级别
        String path = fileInfo.getPath();
        if (StringUtils.isBlank(path))
        {
            return null;
        }
        if (path.startsWith(StorageFileConstants.ACCESS_PUBLIC + "/"))
        {
            return StorageFileConstants.ACCESS_PUBLIC;
        }
        if (path.startsWith(StorageFileConstants.ACCESS_PRIVATE + "/"))
        {
            return StorageFileConstants.ACCESS_PRIVATE;
        }
        return null;
    }

    public Long getFileId()
    {
        return fileId;
    }

    public void setFileId(Long fileId)
    {
        this.fileId = fileId;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public String getPreviewUrl()
    {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl)
    {
        this.previewUrl = previewUrl;
    }

    public String getDownloadUrl()
    {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl)
    {
        this.downloadUrl = downloadUrl;
    }

    public String getFileName()
    {
        return fileName;
    }

    public void setFileName(String fileName)
    {
        this.fileName = fileName;
    }

    public String getObjectKey()
    {
        return objectKey;
    }

    public void setObjectKey(String objectKey)
    {
        this.objectKey = objectKey;
    }

    public String getNewFileName()
    {
        return newFileName;
    }

    public void setNewFileName(String newFileName)
    {
        this.newFileName = newFileName;
    }

    public String getOriginalFilename()
    {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename)
    {
        this.originalFilename = originalFilename;
    }

    public String getPlatform()
    {
        return platform;
    }

    public void setPlatform(String platform)
    {
        this.platform = platform;
    }

    public String getUploadScene()
    {
        return uploadScene;
    }

    public void setUploadScene(String uploadScene)
    {
        this.uploadScene = uploadScene;
    }

    public String getUploadId()
    {
        return uploadId;
    }

    public void setUploadId(String uploadId)
    {
        this.uploadId = uploadId;
    }

    public Integer getUploadStatus()
    {
        return uploadStatus;
    }

    public void setUploadStatus(Integer uploadStatus)
    {
        this.uploadStatus = uploadStatus;
    }

    public String getThUrl()
    {
        return thUrl;
    }

    public void setThUrl(String thUrl)
    {
        this.thUrl = thUrl;
    }

    public String getThPreviewUrl()
    {
        return thPreviewUrl;
    }

    public void setThPreviewUrl(String thPreviewUrl)
    {
        this.thPreviewUrl = thPreviewUrl;
    }

    public String getThObjectKey()
    {
        return thObjectKey;
    }

    public void setThObjectKey(String thObjectKey)
    {
        this.thObjectKey = thObjectKey;
    }

    public String getThFileName()
    {
        return thFileName;
    }

    public void setThFileName(String thFileName)
    {
        this.thFileName = thFileName;
    }

    public String getThNewFileName()
    {
        return thNewFileName;
    }

    public void setThNewFileName(String thNewFileName)
    {
        this.thNewFileName = thNewFileName;
    }

    public Long getSize()
    {
        return size;
    }

    public void setSize(Long size)
    {
        this.size = size;
    }

    public Long getThSize()
    {
        return thSize;
    }

    public void setThSize(Long thSize)
    {
        this.thSize = thSize;
    }

    public String getContentType()
    {
        return contentType;
    }

    public void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    public String getAccess()
    {
        return access;
    }

    public void setAccess(String access)
    {
        this.access = access;
    }
}
