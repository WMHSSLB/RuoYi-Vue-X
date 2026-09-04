package com.ruoyi.system.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.SysStorageFile;

/**
 * 存储文件记录 数据层
 *
 * @author ruoyi
 */
public interface SysStorageFileMapper
{
    /**
     * 根据 URL 查询文件记录
     *
     * @param url 文件访问地址
     * @return 文件记录
     */
    public SysStorageFile selectStorageFileByUrl(String url);

    /**
     * 查询可清理的文件记录
     *
     * @param tempBefore 临时文件过期时间
     * @param waitDeleteBefore 待删除文件到期时间
     * @return 文件记录集合
     */
    public List<SysStorageFile> selectCleanableStorageFiles(@Param("tempBefore") LocalDateTime tempBefore,
            @Param("waitDeleteBefore") LocalDateTime waitDeleteBefore);

    /**
     * 新增文件记录
     *
     * @param storageFile 文件记录
     * @return 结果
     */
    public int insertStorageFile(SysStorageFile storageFile);

    /**
     * 更新文件记录
     *
     * @param storageFile 文件记录
     * @return 结果
     */
    public int updateStorageFile(SysStorageFile storageFile);

    /**
     * 根据 URL 删除文件记录
     *
     * @param url 文件访问地址
     * @return 结果
     */
    public int deleteStorageFileByUrl(String url);

    /**
     * 绑定文件到业务
     *
     * @param url 文件访问地址
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @return 结果
     */
    public int bindStorageFile(@Param("url") String url, @Param("bizType") String bizType,
            @Param("bizId") String bizId, @Param("updateBy") String updateBy);

    /**
     * 标记文件为待删除
     *
     * @param url 文件访问地址
     * @param deleteTime 删除时间
     * @param updateBy 更新者
     * @return 结果
     */
    public int markStorageFileWaitDelete(@Param("url") String url, @Param("deleteTime") LocalDateTime deleteTime,
            @Param("updateBy") String updateBy);

    /**
     * 标记文件为已删除
     *
     * @param url 文件访问地址
     * @param updateBy 更新者
     * @return 结果
     */
    public int markStorageFileDeleted(@Param("url") String url, @Param("updateBy") String updateBy);
}
