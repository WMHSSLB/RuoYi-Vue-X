package com.ruoyi.system.service;

import java.util.List;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.recorder.FileRecorder;
import com.ruoyi.system.domain.SysStorageFile;

/**
 * 存储文件记录 服务层
 *
 * @author ruoyi
 */
public interface ISysStorageFileService extends FileRecorder
{
    /**
     * 绑定文件到业务
     *
     * @param url 文件访问地址
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @return 结果
     */
    public int bindStorageFile(String url, String bizType, String bizId, String updateBy);

    /**
     * 标记文件为待删除
     *
     * @param url 文件访问地址
     * @param delayHours 延迟删除小时数
     * @param updateBy 更新者
     * @return 结果
     */
    public int markStorageFileWaitDelete(String url, int delayHours, String updateBy);

    /**
     * 按业务类型标记文件为待删除
     *
     * @param url 文件访问地址
     * @param bizType 业务类型
     * @param updateBy 更新者
     * @return 结果
     */
    public int markStorageFileWaitDelete(String url, String bizType, String updateBy);

    /**
     * 标记文件为已删除
     *
     * @param url 文件访问地址
     * @param updateBy 更新者
     * @return 结果
     */
    public int markStorageFileDeleted(String url, String updateBy);

    /**
     * 查询可清理的文件记录
     *
     * @param tempExpireHours 临时文件过期小时数
     * @return 文件记录集合
     */
    public List<SysStorageFile> selectCleanableStorageFiles(int tempExpireHours);

    /**
     * 将数据库记录还原为 X File Storage 文件信息
     *
     * @param storageFile 数据库文件记录
     * @return X File Storage 文件信息
     */
    public FileInfo toFileInfo(SysStorageFile storageFile);
}
