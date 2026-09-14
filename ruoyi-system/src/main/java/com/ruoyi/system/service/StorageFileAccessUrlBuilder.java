package com.ruoyi.system.service;

import org.springframework.stereotype.Component;
import com.ruoyi.common.constant.StorageFileConstants;
import com.ruoyi.common.enums.StorageFileAccess;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.StorageFileProperties;
import com.ruoyi.system.domain.SysStorageFile;

/**
 * 文件对外访问地址构建器
 * <p>
 * 数据库保存真实存储地址，接口返回时统一转换为用户可访问地址，避免私有文件暴露真实存储地址
 *
 * @author 955Kane
 */
@Component
public class StorageFileAccessUrlBuilder
{
    /**
     * 存储文件配置
     */
    private final StorageFileProperties storageFileProperties;

    /**
     * 构造文件访问地址构建器
     *
     * @param storageFileProperties 存储文件配置
     */
    public StorageFileAccessUrlBuilder(StorageFileProperties storageFileProperties)
    {
        this.storageFileProperties = storageFileProperties;
    }

    /**
     * 构建原图预览地址
     *
     * @param storageFile 文件记录
     * @return 对外预览地址
     */
    public String buildPreviewUrl(SysStorageFile storageFile)
    {
        return buildPreviewUrl(storageFile, false);
    }

    /**
     * 构建缩略图预览地址
     *
     * @param storageFile 文件记录
     * @return 对外缩略图预览地址
     */
    public String buildThumbnailPreviewUrl(SysStorageFile storageFile)
    {
        // 未生成缩略图时不返回缩略图代理地址，避免前端误认为存在缩略图
        if (storageFile == null || StringUtils.isBlank(storageFile.getThUrl()))
        {
            return null;
        }
        return buildPreviewUrl(storageFile, true);
    }

    /**
     * 构建下载地址
     *
     * @param storageFile 文件记录
     * @return 对外下载地址
     */
    public String buildDownloadUrl(SysStorageFile storageFile)
    {
        if (storageFile == null || storageFile.getFileId() == null)
        {
            return null;
        }
        if (isPublic(storageFile) && StorageFileProperties.ACCESS_URL_MODE_DIRECT.equals(getAccessUrlMode()))
        {
            return storageFile.getUrl();
        }
        if (isPublic(storageFile) && StorageFileProperties.ACCESS_URL_MODE_DOMAIN.equals(getAccessUrlMode()))
        {
            return buildDomainUrl(storageFile.getObjectKey(), storageFile.getUrl());
        }
        return StorageFileConstants.DOWNLOAD_PATH + storageFile.getFileId();
    }

    /**
     * 判断响应中是否允许返回真实存储 URL
     *
     * @param storageFile 文件记录
     * @return true 表示允许返回真实 URL
     */
    public boolean allowExposeRealUrl(SysStorageFile storageFile)
    {
        // 私有文件永远不向前端暴露真实存储地址，避免绕过后端鉴权
        return isPublic(storageFile) && StorageFileProperties.ACCESS_URL_MODE_DIRECT.equals(getAccessUrlMode());
    }

    /**
     * 构建预览地址
     *
     * @param storageFile 文件记录
     * @param thumbnail true 表示构建缩略图地址
     * @return 对外预览地址
     */
    private String buildPreviewUrl(SysStorageFile storageFile, boolean thumbnail)
    {
        if (storageFile == null || storageFile.getFileId() == null)
        {
            return null;
        }
        if (isPublic(storageFile) && StorageFileProperties.ACCESS_URL_MODE_DIRECT.equals(getAccessUrlMode()))
        {
            return thumbnail && StringUtils.isNotBlank(storageFile.getThUrl()) ? storageFile.getThUrl() : storageFile.getUrl();
        }
        if (isPublic(storageFile) && StorageFileProperties.ACCESS_URL_MODE_DOMAIN.equals(getAccessUrlMode()))
        {
            String objectKey = thumbnail && StringUtils.isNotBlank(storageFile.getThObjectKey()) ? storageFile.getThObjectKey()
                    : storageFile.getObjectKey();
            String fallbackUrl = thumbnail && StringUtils.isNotBlank(storageFile.getThUrl()) ? storageFile.getThUrl()
                    : storageFile.getUrl();
            return buildDomainUrl(objectKey, fallbackUrl);
        }
        return StorageFileConstants.PREVIEW_PATH + storageFile.getFileId() + (thumbnail ? "?thumbnail=true" : "");
    }

    /**
     * 构建文件域名地址
     *
     * @param objectKey 对象 Key
     * @param fallbackUrl 兜底地址
     * @return 文件域名地址
     */
    private String buildDomainUrl(String objectKey, String fallbackUrl)
    {
        // 文件域名或对象 Key 缺失时回退原始地址，保证脚手架默认配置也能工作
        if (StringUtils.isBlank(storageFileProperties.getPublicDomain()) || StringUtils.isBlank(objectKey))
        {
            return fallbackUrl;
        }
        return trimEndSlash(storageFileProperties.getPublicDomain()) + "/" + trimStartSlash(objectKey);
    }

    /**
     * 获取有效访问地址模式
     *
     * @return direct、domain 或 backend
     */
    private String getAccessUrlMode()
    {
        String mode = storageFileProperties.getAccessUrlMode();
        if (StorageFileProperties.ACCESS_URL_MODE_DOMAIN.equals(mode)
                || StorageFileProperties.ACCESS_URL_MODE_BACKEND.equals(mode))
        {
            return mode;
        }
        return StorageFileProperties.ACCESS_URL_MODE_DIRECT;
    }

    /**
     * 判断文件是否公开
     *
     * @param storageFile 文件记录
     * @return true 表示公开文件
     */
    private boolean isPublic(SysStorageFile storageFile)
    {
        return storageFile != null && StorageFileAccess.isPublic(storageFile.getAccess());
    }

    /**
     * 去掉结尾斜杠
     *
     * @param value 字符串
     * @return 去掉结尾斜杠后的字符串
     */
    private String trimEndSlash(String value)
    {
        String text = value == null ? "" : value.trim();
        while (text.endsWith("/"))
        {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    /**
     * 去掉开头斜杠
     *
     * @param value 字符串
     * @return 去掉开头斜杠后的字符串
     */
    private String trimStartSlash(String value)
    {
        String text = value == null ? "" : value.trim();
        while (text.startsWith("/"))
        {
            text = text.substring(1);
        }
        return text;
    }
}
