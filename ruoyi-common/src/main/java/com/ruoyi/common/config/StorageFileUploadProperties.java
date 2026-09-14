package com.ruoyi.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 存储文件上传控制配置
 * <p>
 * 用于控制通用上传入口是否必须声明上传场景，避免新业务绕过场景级大小、格式和访问策略
 *
 * @author 955Kane
 */
@Component
@ConfigurationProperties(prefix = "ruoyi.storage-file")
public class StorageFileUploadProperties
{
    /**
     * 是否要求通用上传必须传 uploadScene
     */
    private boolean requireUploadScene = false;

    /**
     * 单次批量上传最大文件数量
     */
    private int maxBatchUploadCount = 20;

    public boolean isRequireUploadScene()
    {
        return requireUploadScene;
    }

    public void setRequireUploadScene(boolean requireUploadScene)
    {
        this.requireUploadScene = requireUploadScene;
    }

    public int getMaxBatchUploadCount()
    {
        return maxBatchUploadCount;
    }

    public void setMaxBatchUploadCount(int maxBatchUploadCount)
    {
        this.maxBatchUploadCount = maxBatchUploadCount;
    }

    /**
     * 获取有效的单次批量上传最大文件数量
     *
     * @return 单次批量上传最大文件数量
     */
    public int getEffectiveMaxBatchUploadCount()
    {
        // 配置小于等于 0 时使用 20 个兜底，避免误配置导致批量上传不受限制
        return maxBatchUploadCount > 0 ? maxBatchUploadCount : 20;
    }
}
