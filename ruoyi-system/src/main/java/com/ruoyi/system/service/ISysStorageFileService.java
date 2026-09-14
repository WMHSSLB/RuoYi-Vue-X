package com.ruoyi.system.service;

import java.util.Collection;
import java.util.List;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.recorder.FileRecorder;
import com.ruoyi.common.core.domain.model.StorageFileUploadRespDTO;
import com.ruoyi.system.domain.SysStorageFile;

/**
 * 存储文件记录服务层
 * <p>
 * 同时实现 X File Storage 的 FileRecorder 接口，用于保存上传记录、分片记录和业务文件生命周期状态
 *
 * @author ruoyi
 */
public interface ISysStorageFileService extends FileRecorder
{
    /**
     * 根据 URL 查询存储文件记录
     *
     * @param url 文件访问地址
     * @return 存储文件记录
     */
    public SysStorageFile selectStorageFileByUrl(String url);

    /**
     * 根据文件ID查询存储文件记录
     *
     * @param fileId 文件ID
     * @return 存储文件记录
     */
    public SysStorageFile selectStorageFileById(Long fileId);

    /**
     * 根据文件ID集合查询存储文件记录
     *
     * @param fileIds 文件ID集合
     * @return 存储文件记录集合
     */
    public List<SysStorageFile> selectStorageFileByIds(List<Long> fileIds);

    /**
     * 查询存储文件记录列表
     *
     * @param storageFile 查询条件
     * @return 存储文件记录集合
     */
    public List<SysStorageFile> selectStorageFileList(SysStorageFile storageFile);

    /**
     * 根据业务类型和业务ID查询已绑定的存储文件记录
     *
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @return 存储文件记录集合
     */
    public List<SysStorageFile> selectStorageFileListByBiz(String bizType, String bizId);

    /**
     * 根据文件ID集合构造已授权的文件回显列表
     * <p>
     * 调用方必须先完成业务权限校验，本方法只负责生成文件回显数据和私有文件短期访问地址
     *
     * @param fileIds 文件ID集合
     * @return 文件回显 DTO 列表
     */
    public List<StorageFileUploadRespDTO> buildAuthorizedFileListByIds(Collection<Long> fileIds);

    /**
     * 根据文件ID集合构造分享式授权文件回显列表
     * <p>
     * 调用方必须先完成业务权限校验，返回的私有文件 token 不绑定用户，适合明确需要短期分享链接的场景
     *
     * @param fileIds 文件ID集合
     * @return 文件回显 DTO 列表
     */
    public List<StorageFileUploadRespDTO> buildShareAuthorizedFileListByIds(Collection<Long> fileIds);

    /**
     * 根据文件ID集合构造绑定当前用户的文件回显列表
     * <p>
     * 适用于合同、财务、证件等高敏感业务，调用方必须先完成业务权限校验
     *
     * @param fileIds 文件ID集合
     * @return 文件回显 DTO 列表
     */
    public List<StorageFileUploadRespDTO> buildUserBoundAuthorizedFileListByIds(Collection<Long> fileIds);

    /**
     * 根据文件ID集合批量绑定文件到业务
     *
     * @param fileIds 文件ID集合
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @return 影响行数
     */
    public int bindStorageFilesByIds(Collection<Long> fileIds, String bizType, String bizId, String updateBy);

    /**
     * 根据文件ID集合同步业务文件集合
     * <p>
     * 新文件会绑定到当前业务，旧文件中不再出现的文件会标记待删除
     *
     * @param newFileIds 当前业务保存后的文件ID集合
     * @param oldFileIds 修改前业务已关联的文件ID集合
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @return 影响行数
     */
    public int syncBizStorageFilesByIds(Collection<Long> newFileIds, Collection<Long> oldFileIds, String bizType,
            String bizId, String updateBy);

    /**
     * 校验分片上传会话是否属于当前登录用户
     *
     * @param url 文件访问地址
     */
    public void checkMultipartOwner(String url);

    /**
     * 标记文件为待删除
     *
     * @param url 文件访问地址
     * @param delayHours 延迟删除小时数
     * @param updateBy 更新者
     * @return 影响行数
     */
    public int markStorageFileWaitDelete(String url, int delayHours, String updateBy);

    /**
     * 按业务类型标记文件为待删除
     *
     * @param url 文件访问地址
     * @param bizType 业务类型
     * @param updateBy 更新者
     * @return 影响行数
     */
    public int markStorageFileWaitDelete(String url, String bizType, String updateBy);

    /**
     * 根据文件ID集合批量标记文件为待删除
     *
     * @param fileIds 文件ID集合
     * @param bizType 业务类型
     * @param updateBy 更新者
     * @return 影响行数
     */
    public int markStorageFilesWaitDeleteByIds(Collection<Long> fileIds, String bizType, String bizId, String updateBy);

    /**
     * 根据业务类型和业务ID批量标记文件为待删除
     *
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @return 影响行数
     */
    public int markStorageFilesWaitDeleteByBiz(String bizType, String bizId, String updateBy);

    /**
     * 根据业务类型集合和业务ID批量标记文件为待删除
     *
     * @param bizTypes 业务类型集合
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @return 影响行数
     */
    public int markStorageFilesWaitDeleteByBiz(Collection<String> bizTypes, String bizId, String updateBy);

    /**
     * 标记文件为已删除
     *
     * @param url 文件访问地址
     * @param updateBy 更新者
     * @return 影响行数
     */
    public int markStorageFileDeleted(String url, String updateBy);

    /**
     * 根据文件ID标记文件为待删除
     *
     * @param fileId 文件ID
     * @param updateBy 更新者
     * @return 影响行数
     */
    public int markStorageFileWaitDeleteById(Long fileId, String updateBy);

    /**
     * 立即删除存储文件
     *
     * @param fileId 文件ID
     * @param updateBy 更新者
     * @return true 表示删除成功
     */
    public boolean deleteStorageFileNow(Long fileId, String updateBy);

    /**
     * 重试删除待删除文件
     *
     * @param fileId 文件ID
     * @param updateBy 更新者
     * @return true 表示删除成功
     */
    public boolean retryDeleteStorageFile(Long fileId, String updateBy);

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
