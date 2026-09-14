package com.ruoyi.system.service;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import com.ruoyi.common.core.domain.model.StorageFileUploadRespDTO;

/**
 * 业务文件字段定义
 *
 * @param <T> 业务实体类型
 * @author ruoyi
 */
public class BusinessStorageFileField<T>
{
    /**
     * 文件业务类型
     */
    private final String bizType;

    /**
     * 业务实体文件ID读取器
     */
    private final Function<T, String> fileIdsGetter;

    /**
     * 业务实体文件回显列表写入器
     */
    private final BiConsumer<T, List<StorageFileUploadRespDTO>> fileListSetter;

    /**
     * 构造业务文件字段定义
     *
     * @param bizType 文件业务类型
     * @param fileIdsGetter 业务实体文件ID读取器
     * @param fileListSetter 业务实体文件回显列表写入器
     */
    private BusinessStorageFileField(String bizType, Function<T, String> fileIdsGetter,
            BiConsumer<T, List<StorageFileUploadRespDTO>> fileListSetter)
    {
        this.bizType = bizType;
        this.fileIdsGetter = fileIdsGetter;
        this.fileListSetter = fileListSetter;
    }

    /**
     * 创建业务文件字段定义
     *
     * @param bizType 文件业务类型
     * @param fileIdsGetter 业务实体文件ID读取器
     * @param fileListSetter 业务实体文件回显列表写入器
     * @param <T> 业务实体类型
     * @return 业务文件字段定义
     */
    public static <T> BusinessStorageFileField<T> of(String bizType, Function<T, String> fileIdsGetter,
            BiConsumer<T, List<StorageFileUploadRespDTO>> fileListSetter)
    {
        return new BusinessStorageFileField<T>(bizType, fileIdsGetter, fileListSetter);
    }

    public String getBizType()
    {
        return bizType;
    }

    public Function<T, String> getFileIdsGetter()
    {
        return fileIdsGetter;
    }

    public BiConsumer<T, List<StorageFileUploadRespDTO>> getFileListSetter()
    {
        return fileListSetter;
    }
}
