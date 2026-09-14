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
    public SysStorageFile selectStorageFileByUrl(@Param("urlHash") String urlHash, @Param("url") String url);

    /**
     * 根据文件ID查询文件记录
     *
     * @param fileId 文件ID
     * @return 文件记录
     */
    public SysStorageFile selectStorageFileById(@Param("fileId") Long fileId);

    /**
     * 根据文件ID集合查询文件记录
     *
     * @param fileIds 文件ID集合
     * @return 文件记录集合
     */
    public List<SysStorageFile> selectStorageFileByIds(@Param("fileIds") List<Long> fileIds);

    /**
     * 查询文件记录列表
     *
     * @param storageFile 文件记录查询条件
     * @return 文件记录集合
     */
    public List<SysStorageFile> selectStorageFileList(SysStorageFile storageFile);

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
    public int deleteStorageFileByUrl(@Param("urlHash") String urlHash, @Param("url") String url);

    /**
     * 根据文件ID集合批量绑定文件到业务
     *
     * @param fileIds 文件ID集合
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @return 结果
     */
    public int bindStorageFilesByIds(@Param("fileIds") List<Long> fileIds, @Param("bizType") String bizType,
            @Param("bizId") String bizId, @Param("updateBy") String updateBy, @Param("createBy") String createBy,
            @Param("tempStatus") String tempStatus, @Param("usedStatus") String usedStatus,
            @Param("deletedStatus") String deletedStatus);

    /**
     * 标记文件为待删除
     *
     * @param url 文件访问地址
     * @param deleteTime 删除时间
     * @param updateBy 更新者
     * @return 结果
     */
    public int markStorageFileWaitDelete(@Param("urlHash") String urlHash, @Param("url") String url,
            @Param("deleteTime") LocalDateTime deleteTime, @Param("updateBy") String updateBy);

    /**
     * 根据文件ID集合批量标记文件为待删除
     *
     * @param fileIds 文件ID集合
     * @param deleteTime 删除时间
     * @param updateBy 更新者
     * @return 结果
     */
    public int markStorageFilesWaitDeleteByIds(@Param("fileIds") List<Long> fileIds, @Param("bizType") String bizType,
            @Param("bizId") String bizId, @Param("deleteTime") LocalDateTime deleteTime, @Param("updateBy") String updateBy,
            @Param("deletedStatus") String deletedStatus);

    /**
     * 根据业务类型和业务ID批量标记文件为待删除
     *
     * @param bizTypes 业务类型集合
     * @param bizId 业务ID
     * @param deleteTime 删除时间
     * @param updateBy 更新者
     * @return 结果
     */
    public int markStorageFilesWaitDeleteByBiz(@Param("bizTypes") List<String> bizTypes, @Param("bizId") String bizId,
            @Param("deleteTime") LocalDateTime deleteTime, @Param("updateBy") String updateBy);

    /**
     * 标记文件为已删除
     *
     * @param url 文件访问地址
     * @param updateBy 更新者
     * @return 结果
     */
    public int markStorageFileDeleted(@Param("urlHash") String urlHash, @Param("url") String url,
            @Param("updateBy") String updateBy);

    /**
     * 根据文件ID更新文件状态
     *
     * @param fileId 文件ID
     * @param fileStatus 文件状态
     * @param updateBy 更新者
     * @return 结果
     */
    public int updateStorageFileStatus(@Param("fileId") Long fileId, @Param("fileStatus") String fileStatus,
            @Param("updateBy") String updateBy);
}
