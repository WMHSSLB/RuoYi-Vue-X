package com.ruoyi.system.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.SysStorageFilePart;

/**
 * 存储文件分片记录 数据层
 *
 * @author ruoyi
 */
public interface SysStorageFilePartMapper
{
    /**
     * 新增或更新分片记录
     *
     * @param storageFilePart 分片记录
     * @return 结果
     */
    public int saveStorageFilePart(SysStorageFilePart storageFilePart);

    /**
     * 根据分片上传ID删除分片记录
     *
     * @param uploadId 分片上传ID
     * @return 结果
     */
    public int deleteStorageFilePartByUploadId(@Param("uploadId") String uploadId);
}
