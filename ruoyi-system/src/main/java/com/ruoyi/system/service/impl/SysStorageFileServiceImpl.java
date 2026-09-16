package com.ruoyi.system.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.hash.HashInfo;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.ruoyi.common.constant.StorageFileConstants;
import com.ruoyi.common.core.domain.model.StorageFileUploadRespDTO;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.SysStorageFile;
import com.ruoyi.system.domain.SysStorageFilePart;
import com.ruoyi.system.mapper.SysStorageFileMapper;
import com.ruoyi.system.mapper.SysStorageFilePartMapper;
import com.ruoyi.system.service.ISysStorageFileService;
import com.ruoyi.system.service.StorageFileLifecyclePolicyService;
import com.ruoyi.system.service.StorageFileLifecyclePolicyService.CleanableStorageFileTimeRange;
import com.ruoyi.system.service.StorageFileResponseAssembler;

/**
 * 存储文件记录 服务层实现
 * <p>
 * 该类同时实现 X File Storage 的 FileRecorder 接口，让上传记录真正落到 sys_storage_file
 *
 * @author 955Kane
 */
@Service
public class SysStorageFileServiceImpl implements ISysStorageFileService
{
    @Autowired
    private SysStorageFileMapper storageFileMapper;

    @Autowired
    private SysStorageFilePartMapper storageFilePartMapper;

    @Autowired
    private StorageFileLifecyclePolicyService storageFileLifecyclePolicyService;

    @Autowired
    private ObjectProvider<FileStorageService> fileStorageServiceProvider;

    @Autowired
    private StorageFileResponseAssembler storageFileResponseAssembler;

    /**
     * 根据 URL 查询存储文件记录
     *
     * @param url 文件访问地址
     * @return 存储文件记录
     */
    @Override
    public SysStorageFile selectStorageFileByUrl(String url)
    {
        // URL 为空时无法定位文件记录，直接返回 null 交给调用方判断
        if (StringUtils.isBlank(url))
        {
            return null;
        }
        return storageFileMapper.selectStorageFileByUrl(sha256Hex(url), url);
    }

    /**
     * 根据文件ID查询存储文件记录
     *
     * @param fileId 文件ID
     * @return 存储文件记录
     */
    @Override
    public SysStorageFile selectStorageFileById(Long fileId)
    {
        // 文件ID为空时无法定位记录，直接返回 null 交给调用方判断
        if (fileId == null)
        {
            return null;
        }
        return storageFileMapper.selectStorageFileById(fileId);
    }

    /**
     * 根据文件ID集合查询存储文件记录
     *
     * @param fileIds 文件ID集合
     * @return 存储文件记录集合
     */
    @Override
    public List<SysStorageFile> selectStorageFileByIds(List<Long> fileIds)
    {
        // 空集合直接返回空列表，避免 Mapper 生成非法 in () SQL
        if (fileIds == null || fileIds.isEmpty())
        {
            return new ArrayList<SysStorageFile>();
        }
        return storageFileMapper.selectStorageFileByIds(fileIds);
    }

    /**
     * 查询存储文件记录列表
     *
     * @param storageFile 查询条件
     * @return 存储文件记录集合
     */
    @Override
    public List<SysStorageFile> selectStorageFileList(SysStorageFile storageFile)
    {
        // 查询条件为空时使用空对象，避免 Mapper 判空复杂化
        return storageFileMapper.selectStorageFileList(storageFile == null ? new SysStorageFile() : storageFile);
    }

    /**
     * 根据业务类型和业务ID查询已绑定的存储文件记录
     *
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @return 存储文件记录集合
     */
    @Override
    public List<SysStorageFile> selectStorageFileListByBiz(String bizType, String bizId)
    {
        // 业务身份必须成对出现，避免空条件误查出全量文件
        if (StringUtils.isBlank(bizType) || StringUtils.isBlank(bizId))
        {
            return new ArrayList<SysStorageFile>();
        }
        SysStorageFile query = new SysStorageFile();
        // 只按本系统业务字段查询，不使用 X File Storage 自带的 objectId/objectType
        query.setBizType(bizType);
        query.setBizId(bizId);
        // 业务回显只返回已绑定文件，过滤临时文件、待删除文件和已删除文件
        query.setFileStatus(storageFileLifecyclePolicyService.getUsedStatus());
        return storageFileMapper.selectStorageFileList(query);
    }

    /**
     * 根据文件ID集合构造已授权的文件回显列表
     * <p>
     * 调用方必须先完成业务权限校验，本方法只负责生成文件回显数据和私有文件短期访问地址
     *
     * @param fileIds 文件ID集合
     * @return 文件回显 DTO 列表
     */
    @Override
    public List<StorageFileUploadRespDTO> buildAuthorizedFileListByIds(Collection<Long> fileIds)
    {
        // 业务私有文件默认签发绑定当前登录用户的 token，避免授权链接被复制给其他账号继续访问
        return buildAuthorizedFileListByIds(fileIds, true);
    }

    /**
     * 根据文件ID集合构造分享式授权文件回显列表
     *
     * @param fileIds 文件ID集合
     * @return 文件回显 DTO 列表
     */
    @Override
    public List<StorageFileUploadRespDTO> buildShareAuthorizedFileListByIds(Collection<Long> fileIds)
    {
        // 分享式 token 只绑定 fileId，不绑定当前用户，仅供明确需要短期分享链接的业务主动调用
        return buildAuthorizedFileListByIds(fileIds, false);
    }

    /**
     * 根据文件ID集合构造绑定当前用户的文件回显列表
     * <p>
     * 适用于合同、财务、证件等高敏感业务，调用方必须先完成业务权限校验
     *
     * @param fileIds 文件ID集合
     * @return 文件回显 DTO 列表
     */
    @Override
    public List<StorageFileUploadRespDTO> buildUserBoundAuthorizedFileListByIds(Collection<Long> fileIds)
    {
        return buildAuthorizedFileListByIds(fileIds, true);
    }

    /**
     * 根据文件ID集合构造文件回显列表
     *
     * @param fileIds 文件ID集合
     * @param bindCurrentUser true 表示私有文件 token 绑定当前登录用户
     * @return 文件回显 DTO 列表
     */
    private List<StorageFileUploadRespDTO> buildAuthorizedFileListByIds(Collection<Long> fileIds, boolean bindCurrentUser)
    {
        List<Long> idList = normalizeFileIdList(fileIds);
        if (idList.isEmpty())
        {
            return new ArrayList<StorageFileUploadRespDTO>();
        }
        List<SysStorageFile> storageFiles = selectStorageFileByIds(idList);
        List<StorageFileUploadRespDTO> result = new ArrayList<StorageFileUploadRespDTO>();
        for (SysStorageFile storageFile : storageFiles)
        {
            // 业务层已经完成访问判断，这里给每个文件生成前端可直接使用的回显信息
            result.add(buildAuthorizedFileRespDTO(storageFile, bindCurrentUser));
        }
        return result;
    }

    /**
     * 保存上传文件记录
     *
     * @param fileInfo X File Storage 上传后生成的文件元数据
     * @return true 表示保存成功
     */
    @Override
    public boolean save(FileInfo fileInfo)
    {
        // 上传成功后框架会调用该方法，没有 URL 的记录无法用于后续按 URL 查询
        if (fileInfo == null || StringUtils.isBlank(fileInfo.getUrl()))
        {
            return false;
        }
        // 同一个 URL 重复保存时按更新处理，避免重试上传造成唯一索引冲突
        SysStorageFile exists = storageFileMapper.selectStorageFileByUrl(sha256Hex(fileInfo.getUrl()), fileInfo.getUrl());
        SysStorageFile storageFile = toStorageFile(fileInfo);
        if (exists != null)
        {
            // 保留原主键并刷新最新元数据
            storageFile.setFileId(exists.getFileId());
            storageFile.setUpdateTime(DateUtils.getNowDate());
            return storageFileMapper.updateStorageFile(storageFile) > 0;
        }
        // 新上传文件先记录为临时状态，等业务保存成功后再绑定为已使用
        storageFile.setFileStatus(storageFileLifecyclePolicyService.getInitialStatus());
        // 记录上传人，私有临时文件下载时可判断是否属于当前登录用户
        storageFile.setCreateBy(getCurrentUsernameQuietly());
        storageFile.setCreateTime(fileInfo.getCreateTime() == null ? DateUtils.getNowDate() : fileInfo.getCreateTime());
        return storageFileMapper.insertStorageFile(storageFile) > 0;
    }

    /**
     * 更新上传文件记录
     *
     * @param fileInfo X File Storage 文件元数据
     */
    @Override
    public void update(FileInfo fileInfo)
    {
        // 框架更新记录时复用保存逻辑，保证 URL 对应的元数据保持最新
        save(fileInfo);
    }

    /**
     * 根据 URL 查询文件信息
     *
     * @param url 文件访问地址
     * @return X File Storage 文件信息
     */
    @Override
    public FileInfo getByUrl(String url)
    {
        // 按 URL 从 sys_storage_file 还原 FileInfo，支持官网文档中的 delete(url)、exists(url) 等操作
        return toFileInfo(storageFileMapper.selectStorageFileByUrl(sha256Hex(url), url));
    }

    /**
     * 删除文件记录
     *
     * @param url 文件访问地址
     * @return true 表示删除记录成功
     */
    @Override
    public boolean delete(String url)
    {
        // 物理删除成功后框架会调用该方法，这里保留记录并标记已删除，便于审计和排查
        return markStorageFileDeleted(url, "") >= 0;
    }

    /**
     * 保存分片上传记录
     *
     * @param filePartInfo 分片上传元数据
     */
    @Override
    public void saveFilePart(FilePartInfo filePartInfo)
    {
        // 分片上传没有 uploadId 时无法在后续合并或取消时定位同一批分片
        if (filePartInfo == null || StringUtils.isBlank(filePartInfo.getUploadId()))
        {
            return;
        }
        // 将框架回调的分片信息转换为数据库记录，和主文件记录拆开保存
        SysStorageFilePart storageFilePart = toStorageFilePart(filePartInfo);
        storageFilePartMapper.saveStorageFilePart(storageFilePart);
    }

    /**
     * 根据 uploadId 删除分片上传记录
     *
     * @param uploadId 分片上传 ID
     */
    @Override
    public void deleteFilePartByUploadId(String uploadId)
    {
        // 框架完成、取消或清理分片上传时会按 uploadId 删除这批分片记录
        if (StringUtils.isBlank(uploadId))
        {
            return;
        }
        storageFilePartMapper.deleteStorageFilePartByUploadId(uploadId);
    }

    /**
     * 根据文件ID集合批量绑定文件到业务
     *
     * @param fileIds 文件ID集合
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @return 影响行数
     */
    @Override
    public int bindStorageFilesByIds(Collection<Long> fileIds, String bizType, String bizId, String updateBy)
    {
        // 业务身份为空时不允许绑定，避免文件资产归属不完整
        if (StringUtils.isBlank(bizType) || StringUtils.isBlank(bizId))
        {
            return 0;
        }
        List<Long> idList = normalizeFileIdList(fileIds);
        if (idList.isEmpty())
        {
            return 0;
        }
        // 只能绑定当前用户上传的临时文件，或当前业务已经绑定的文件，避免抢绑其它业务文件
        int rows = storageFileMapper.bindStorageFilesByIds(idList, bizType, bizId, updateBy, updateBy,
                storageFileLifecyclePolicyService.getInitialStatus(), storageFileLifecyclePolicyService.getUsedStatus(),
                storageFileLifecyclePolicyService.getDeletedStatus());
        if (rows < idList.size())
        {
            // 绑定数量不足说明存在不存在、已删除、非本人临时文件或其它业务文件，直接回滚业务保存
            throw new ServiceException("文件不存在或无权绑定到当前业务");
        }
        return rows;
    }

    /**
     * 根据文件ID集合同步业务文件集合
     *
     * @param newFileIds 当前业务保存后的文件ID集合
     * @param oldFileIds 修改前业务已关联的文件ID集合
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @return 影响行数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncBizStorageFilesByIds(Collection<Long> newFileIds, Collection<Long> oldFileIds, String bizType,
            String bizId, String updateBy)
    {
        // 先把当前表单仍保存的文件绑定为已使用，防止临时清理任务误删
        int rows = bindStorageFilesByIds(newFileIds, bizType, bizId, updateBy);
        Set<Long> currentFileIds = new HashSet<Long>(normalizeFileIdList(newFileIds));
        if (oldFileIds == null || oldFileIds.isEmpty())
        {
            return rows;
        }
        List<Long> waitDeleteFileIds = new ArrayList<Long>();
        for (Long oldFileId : oldFileIds)
        {
            // 旧文件ID不在新集合中，说明用户从业务表单里移除了这个文件
            if (oldFileId != null && !currentFileIds.contains(oldFileId))
            {
                waitDeleteFileIds.add(oldFileId);
            }
        }
        // 被移除的文件不立即物理删除，先进入待删除状态，后续由清理任务统一处理
        rows += markStorageFilesWaitDeleteByIds(waitDeleteFileIds, bizType, bizId, updateBy);
        return rows;
    }

    /**
     * 校验分片上传会话是否属于当前登录用户
     *
     * @param url 文件访问地址
     */
    @Override
    public void checkMultipartOwner(Long fileId)
    {
        // fileId 是分片后续操作的推荐定位条件，不受代理 URL 和访问域名变化影响
        checkMultipartOwner(selectStorageFileById(fileId));
    }

    @Override
    public void checkMultipartOwner(String url)
    {
        // URL 版本保留给旧前端和旧调用方兼容使用
        checkMultipartOwner(selectStorageFileByUrl(url));
    }

    /**
     * 校验分片上传会话是否属于当前登录用户
     *
     * @param storageFile 存储文件记录
     */
    private void checkMultipartOwner(SysStorageFile storageFile)
    {
        if (storageFile == null)
        {
            throw new ServiceException("文件记录不存在");
        }
        if (StringUtils.isBlank(storageFile.getUploadId()))
        {
            throw new ServiceException("文件不是有效的分片上传会话");
        }
        // 超级管理员允许兜底处理异常分片任务，普通用户只能操作自己初始化的分片上传
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        String username = SecurityUtils.getUsername();
        if (!username.equals(storageFile.getCreateBy()))
        {
            throw new ServiceException("只能操作自己创建的分片上传任务");
        }
    }

    /**
     * 标记文件为待删除
     *
     * @param url 文件访问地址
     * @param delayHours 延迟删除小时数
     * @param updateBy 更新者
     * @return 结果
     */
    @Override
    public int markStorageFileWaitDelete(String url, int delayHours, String updateBy)
    {
        // 历史本地 /profile 文件没有 X File Storage 记录，直接跳过状态标记
        if (StringUtils.isBlank(url))
        {
            return 0;
        }
        // 延迟删除给浏览器缓存、CDN 缓存和短时间回滚留出缓冲
        LocalDateTime deleteTime = storageFileLifecyclePolicyService.resolveDeleteTime(delayHours);
        return storageFileMapper.markStorageFileWaitDelete(sha256Hex(url), url, deleteTime, updateBy);
    }

    /**
     * 按业务类型标记文件为待删除
     *
     * @param url 文件访问地址
     * @param bizType 业务类型
     * @param updateBy 更新者
     * @return 结果
     */
    @Override
    public int markStorageFileWaitDelete(String url, String bizType, String updateBy)
    {
        // 历史本地 /profile 文件没有 X File Storage 记录，直接跳过状态标记
        if (StringUtils.isBlank(url))
        {
            return 0;
        }
        // 不同业务可以配置不同保留时间，例如头像短保留、合同附件长保留
        LocalDateTime deleteTime = storageFileLifecyclePolicyService.resolveDeleteTime(bizType);
        return storageFileMapper.markStorageFileWaitDelete(sha256Hex(url), url, deleteTime, updateBy);
    }

    /**
     * 根据文件ID集合批量标记文件为待删除
     *
     * @param fileIds 文件ID集合
     * @param bizType 业务类型
     * @param updateBy 更新者
     * @return 影响行数
     */
    @Override
    public int markStorageFilesWaitDeleteByIds(Collection<Long> fileIds, String bizType, String bizId, String updateBy)
    {
        List<Long> idList = normalizeFileIdList(fileIds);
        if (idList.isEmpty() || StringUtils.isBlank(bizType) || StringUtils.isBlank(bizId))
        {
            return 0;
        }
        LocalDateTime deleteTime = storageFileLifecyclePolicyService.resolveDeleteTime(bizType);
        // 按主键批量标记待删除时必须带业务归属，避免误删其它业务的同 ID 文件引用
        return storageFileMapper.markStorageFilesWaitDeleteByIds(idList, bizType, bizId, deleteTime, updateBy,
                storageFileLifecyclePolicyService.getDeletedStatus());
    }

    /**
     * 根据业务类型和业务ID批量标记文件为待删除
     *
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @return 影响行数
     */
    @Override
    public int markStorageFilesWaitDeleteByBiz(String bizType, String bizId, String updateBy)
    {
        List<String> bizTypes = new ArrayList<String>();
        if (StringUtils.isNotBlank(bizType))
        {
            bizTypes.add(bizType);
        }
        return markStorageFilesWaitDeleteByBiz(bizTypes, bizId, updateBy);
    }

    /**
     * 根据业务类型集合和业务ID批量标记文件为待删除
     *
     * @param bizTypes 业务类型集合
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @return 影响行数
     */
    @Override
    public int markStorageFilesWaitDeleteByBiz(Collection<String> bizTypes, String bizId, String updateBy)
    {
        List<String> typeList = normalizeBizTypeList(bizTypes);
        if (typeList.isEmpty() || StringUtils.isBlank(bizId))
        {
            return 0;
        }
        LocalDateTime deleteTime = storageFileLifecyclePolicyService.resolveDeleteTime(typeList);
        // 删除整条业务数据时，按业务ID和多个文件场景一次标记该业务下的所有文件
        return storageFileMapper.markStorageFilesWaitDeleteByBiz(typeList, bizId, deleteTime, updateBy);
    }

    /**
     * 标记文件为已删除
     *
     * @param url 文件访问地址
     * @param updateBy 更新者
     * @return 结果
     */
    @Override
    public int markStorageFileDeleted(String url, String updateBy)
    {
        // 物理文件删除成功后保留记录并标记删除，便于后续审计和排查
        if (StringUtils.isBlank(url))
        {
            return 0;
        }
        return storageFileMapper.markStorageFileDeleted(sha256Hex(url), url, updateBy);
    }

    /**
     * 根据文件ID标记文件为待删除
     *
     * @param fileId 文件ID
     * @param updateBy 更新者
     * @return 影响行数
     */
    @Override
    public int markStorageFileWaitDeleteById(Long fileId, String updateBy)
    {
        SysStorageFile storageFile = selectStorageFileById(fileId);
        if (storageFile == null)
        {
            return 0;
        }
        return markStorageFileWaitDelete(storageFile.getUrl(), storageFile.getBizType(), updateBy);
    }

    /**
     * 立即删除存储文件
     *
     * @param fileId 文件ID
     * @param updateBy 更新者
     * @return true 表示删除成功
     */
    @Override
    public boolean deleteStorageFileNow(Long fileId, String updateBy)
    {
        SysStorageFile storageFile = selectStorageFileById(fileId);
        if (storageFile == null)
        {
            throw new ServiceException("文件记录不存在");
        }
        FileInfo fileInfo = toFileInfo(storageFile);
        if (fileInfo == null)
        {
            throw new ServiceException("文件记录不存在");
        }
        // 物理删除成功后 FileRecorder.delete 会回调标记已删除，这里再兜底标记一次保证状态一致
        boolean deleted = fileStorageServiceProvider.getObject().delete(fileInfo);
        if (deleted)
        {
            markStorageFileDeleted(storageFile.getUrl(), updateBy);
        }
        return deleted;
    }

    /**
     * 重试删除待删除文件
     *
     * @param fileId 文件ID
     * @param updateBy 更新者
     * @return true 表示删除成功
     */
    @Override
    public boolean retryDeleteStorageFile(Long fileId, String updateBy)
    {
        SysStorageFile storageFile = selectStorageFileById(fileId);
        if (storageFile == null)
        {
            throw new ServiceException("文件记录不存在");
        }
        storageFileLifecyclePolicyService.assertRetryDeleteAllowed(storageFile.getFileStatus());
        return deleteStorageFileNow(fileId, updateBy);
    }

    /**
     * 查询可清理的文件记录
     *
     * @param tempExpireHours 临时文件过期小时数
     * @return 文件记录集合
     */
    @Override
    public List<SysStorageFile> selectCleanableStorageFiles(int tempExpireHours)
    {
        // 生命周期策略服务统一计算临时文件过期时间和待删除文件到期时间
        CleanableStorageFileTimeRange timeRange = storageFileLifecyclePolicyService
                .resolveCleanableTimeRange(tempExpireHours);
        return storageFileMapper.selectCleanableStorageFiles(timeRange.getTempBefore(),
                timeRange.getWaitDeleteBefore());
    }

    /**
     * 将数据库记录还原为 X File Storage 文件信息
     *
     * @param storageFile 数据库文件记录
     * @return X File Storage 文件信息
     */
    @Override
    public FileInfo toFileInfo(SysStorageFile storageFile)
    {
        // 没有数据库记录时返回 null，让框架或调用方自行决定是否跳过
        if (storageFile == null)
        {
            return null;
        }
        // FileInfo 需要 platform、basePath、path、filename 才能让具体平台定位对象
        FileInfo fileInfo = new FileInfo()
                .setId(storageFile.getStorageId())
                .setUrl(storageFile.getUrl())
                .setSize(storageFile.getSize())
                .setFilename(storageFile.getFilename())
                .setOriginalFilename(storageFile.getOriginalFilename())
                .setBasePath(storageFile.getBasePath())
                .setPath(storageFile.getPath())
                .setExt(storageFile.getExt())
                .setContentType(storageFile.getContentType())
                .setPlatform(storageFile.getPlatform())
                .setThUrl(storageFile.getThUrl())
                .setThFilename(storageFile.getThFilename())
                .setThSize(storageFile.getThSize())
                .setThContentType(storageFile.getThContentType())
                .setObjectId(storageFile.getObjectId())
                .setObjectType(storageFile.getObjectType())
                .setMetadata(parseMap(storageFile.getMetadata()))
                .setUserMetadata(parseMap(storageFile.getUserMetadata()))
                .setThMetadata(parseMap(storageFile.getThMetadata()))
                .setThUserMetadata(parseMap(storageFile.getThUserMetadata()))
                .setFileAcl(parseObject(storageFile.getFileAcl()))
                .setThFileAcl(parseObject(storageFile.getThFileAcl()))
                .setHashInfo(parseHashInfo(storageFile.getHashInfo()))
                .setUploadId(storageFile.getUploadId())
                .setUploadStatus(storageFile.getUploadStatus())
                .setCreateTime(storageFile.getCreateTime());
        return fileInfo;
    }

    /**
     * 构造已授权的文件回显 DTO
     *
     * @param storageFile 文件记录
     * @param bindCurrentUser true 表示私有文件 token 绑定当前登录用户
     * @return 文件回显 DTO
     */
    private StorageFileUploadRespDTO buildAuthorizedFileRespDTO(SysStorageFile storageFile, boolean bindCurrentUser)
    {
        // 业务回显 DTO 统一交给响应组装器处理，避免文件服务和控制器各自维护一套返回字段规则
        return storageFileResponseAssembler.assemble(storageFile, bindCurrentUser);
    }

    /**
     * 将 X File Storage 文件信息转换为数据库记录
     *
     * @param fileInfo X File Storage 文件信息
     * @return 数据库文件记录
     */
    private SysStorageFile toStorageFile(FileInfo fileInfo)
    {
        SysStorageFile storageFile = new SysStorageFile();
        // 原样保存框架生成的元数据，保证后续可按 URL 还原 FileInfo 做删除、下载、判断存在
        storageFile.setStorageId(fileInfo.getId());
        storageFile.setUrl(fileInfo.getUrl());
        storageFile.setUrlHash(sha256Hex(fileInfo.getUrl()));
        storageFile.setObjectKey(buildObjectKey(fileInfo.getBasePath(), fileInfo.getPath(), fileInfo.getFilename()));
        storageFile.setAccess(resolveAccess(fileInfo));
        storageFile.setSize(fileInfo.getSize());
        storageFile.setFilename(fileInfo.getFilename());
        storageFile.setOriginalFilename(fileInfo.getOriginalFilename());
        storageFile.setBasePath(fileInfo.getBasePath());
        storageFile.setPath(fileInfo.getPath());
        storageFile.setExt(fileInfo.getExt());
        storageFile.setContentType(fileInfo.getContentType());
        storageFile.setPlatform(fileInfo.getPlatform());
        storageFile.setThUrl(fileInfo.getThUrl());
        storageFile.setThFilename(fileInfo.getThFilename());
        storageFile.setThObjectKey(buildObjectKey(fileInfo.getBasePath(), fileInfo.getPath(), fileInfo.getThFilename()));
        storageFile.setThSize(fileInfo.getThSize());
        storageFile.setThContentType(fileInfo.getThContentType());
        storageFile.setObjectId(fileInfo.getObjectId());
        storageFile.setObjectType(fileInfo.getObjectType());
        storageFile.setMetadata(toJson(fileInfo.getMetadata()));
        storageFile.setUserMetadata(toJson(fileInfo.getUserMetadata()));
        storageFile.setThMetadata(toJson(fileInfo.getThMetadata()));
        storageFile.setThUserMetadata(toJson(fileInfo.getThUserMetadata()));
        storageFile.setAttr(toJson(fileInfo.getAttr()));
        storageFile.setFileAcl(toJson(fileInfo.getFileAcl()));
        storageFile.setThFileAcl(toJson(fileInfo.getThFileAcl()));
        storageFile.setHashInfo(toJson(fileInfo.getHashInfo()));
        storageFile.setUploadId(fileInfo.getUploadId());
        storageFile.setUploadStatus(fileInfo.getUploadStatus());
        return storageFile;
    }

    /**
     * 将 X File Storage 分片信息转换为数据库记录
     *
     * @param filePartInfo X File Storage 分片信息
     * @return 数据库分片记录
     */
    private SysStorageFilePart toStorageFilePart(FilePartInfo filePartInfo)
    {
        SysStorageFilePart storageFilePart = new SysStorageFilePart();
        // 分片记录只保存断点续传需要的分片维度信息，不混入主文件业务绑定状态
        storageFilePart.setStorageId(filePartInfo.getId());
        storageFilePart.setPlatform(filePartInfo.getPlatform());
        storageFilePart.setUploadId(filePartInfo.getUploadId());
        storageFilePart.setPartNumber(filePartInfo.getPartNumber());
        storageFilePart.setPartSize(filePartInfo.getPartSize());
        storageFilePart.setEtag(filePartInfo.getETag());
        storageFilePart.setHashInfo(toJson(filePartInfo.getHashInfo()));
        storageFilePart.setCreateTime(filePartInfo.getCreateTime() == null ? DateUtils.getNowDate() : filePartInfo.getCreateTime());
        storageFilePart.setLastModified(toLocalDateTime(filePartInfo.getLastModified()));
        return storageFilePart;
    }

    /**
     * 将原始依赖中的 Date 转换为业务记录使用的 LocalDateTime
     *
     * @param date 原始时间
     * @return 本地日期时间
     */
    private LocalDateTime toLocalDateTime(Date date)
    {
        // X File Storage 的 FilePartInfo 使用 Date，这里只在落库前转换一次
        return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * 去空并去重文件ID集合
     *
     * @param fileIds 文件ID集合
     * @return 文件ID列表
     */
    private List<Long> normalizeFileIdList(Collection<Long> fileIds)
    {
        List<Long> result = new ArrayList<Long>();
        if (fileIds == null || fileIds.isEmpty())
        {
            return result;
        }
        Set<Long> fileIdSet = new HashSet<Long>();
        for (Long fileId : fileIds)
        {
            // 空 fileId 和小于等于 0 的值都不参与业务绑定或待删除处理
            if (fileId != null && fileId.longValue() > 0 && fileIdSet.add(fileId))
            {
                result.add(fileId);
            }
        }
        return result;
    }

    /**
     * 去空并去重业务类型集合
     *
     * @param bizTypes 业务类型集合
     * @return 业务类型列表
     */
    private List<String> normalizeBizTypeList(Collection<String> bizTypes)
    {
        List<String> result = new ArrayList<String>();
        if (bizTypes == null || bizTypes.isEmpty())
        {
            return result;
        }
        Set<String> bizTypeSet = new HashSet<String>();
        for (String bizType : bizTypes)
        {
            // 删除整条业务数据时必须显式声明业务类型，避免只按 bizId 误删其它模块同 ID 的文件
            if (StringUtils.isNotBlank(bizType) && bizTypeSet.add(bizType))
            {
                result.add(bizType);
            }
        }
        return result;
    }

    /**
     * 安全获取当前登录用户名
     *
     * @return 当前登录用户名，取不到时返回空字符串
     */
    private String getCurrentUsernameQuietly()
    {
        try
        {
            // 普通上传请求有登录上下文，框架内部或定时任务回调可能没有登录上下文
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return "";
        }
    }

    /**
     * 转换为 JSON 字符串
     *
     * @param value 待转换对象
     * @return JSON 字符串
     */
    private String toJson(Object value)
    {
        // 空值不写 JSON，减少数据库里的无意义空对象
        return value == null ? null : JSON.toJSONString(value);
    }

    /**
     * 解析字符串键值元数据
     *
     * @param json JSON 字符串
     * @return 字符串键值 Map
     */
    private Map<String, String> parseMap(String json)
    {
        // 元数据为空时直接返回 null，保持和 FileInfo 原始语义一致
        if (StringUtils.isBlank(json))
        {
            return null;
        }
        return JSON.parseObject(json, new TypeReference<Map<String, String>>()
        {
        });
    }

    /**
     * 解析普通对象
     *
     * @param json JSON 字符串
     * @return 普通对象
     */
    private Object parseObject(String json)
    {
        // ACL 等字段类型由不同平台决定，这里还原为通用 JSON 对象即可
        return StringUtils.isBlank(json) ? null : JSON.parse(json);
    }

    /**
     * 解析哈希信息
     *
     * @param json JSON 字符串
     * @return 哈希信息
     */
    private HashInfo parseHashInfo(String json)
    {
        // 哈希信息本质是字符串键值 Map，重新包装成 HashInfo 交回框架
        Map<String, String> map = parseMap(json);
        return map == null ? null : new HashInfo(map);
    }

    /**
     * 计算 URL 的 SHA-256 哈希
     *
     * @param url 文件访问地址
     * @return 64 位小写十六进制哈希
     */
    private String sha256Hex(String url)
    {
        // URL 为空时不参与哈希计算，调用方会在进入数据库前完成空值判断
        if (StringUtils.isBlank(url))
        {
            return "";
        }
        try
        {
            // 使用完整 URL 计算固定长度哈希，避免 varchar 长字段前缀索引误判
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(url.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e)
        {
            // JDK 标准算法缺失属于运行环境异常，直接抛出避免写入不可查询的数据
            throw new IllegalStateException("当前运行环境不支持 SHA-256", e);
        }
    }

    /**
     * 构建存储对象Key
     *
     * @param basePath 存储基础路径
     * @param path 文件路径
     * @param filename 文件名
     * @return 对象Key
     */
    private String buildObjectKey(String basePath, String path, String filename)
    {
        if (StringUtils.isBlank(filename))
        {
            return null;
        }
        // 对象 Key 是平台真实对象定位信息，不包含域名，只由基础路径、文件路径和文件名组成
        return normalizeObjectKey(basePath) + normalizeObjectKey(path) + filename;
    }

    /**
     * 清洗对象Key片段
     *
     * @param value 对象Key片段
     * @return 以 / 结尾的片段
     */
    private String normalizeObjectKey(String value)
    {
        if (StringUtils.isBlank(value))
        {
            return "";
        }
        String normalized = value.trim().replace("\\", "/");
        while (normalized.startsWith("/"))
        {
            normalized = normalized.substring(1);
        }
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    /**
     * 根据文件元数据解析访问级别
     *
     * @param fileInfo X File Storage 文件信息
     * @return 访问级别
     */
    private String resolveAccess(FileInfo fileInfo)
    {
        if (fileInfo == null)
        {
            return StorageFileConstants.ACCESS_PRIVATE;
        }
        // 优先根据对象 ACL 判断访问级别，避免业务权限长期依赖 public/private 路径命名
        String fileAcl = String.valueOf(fileInfo.getFileAcl());
        if (StringUtils.isNotBlank(fileAcl) && fileAcl.contains(StorageFileConstants.ACL_PUBLIC_READ))
        {
            return StorageFileConstants.ACCESS_PUBLIC;
        }
        String path = fileInfo.getPath();
        if (StringUtils.isBlank(path))
        {
            return StorageFileConstants.ACCESS_PRIVATE;
        }
        // 历史数据或平台未返回 ACL 时回退旧路径规则，保证已有文件可平滑升级
        return path.startsWith(StorageFileConstants.ACCESS_PUBLIC + "/") ? StorageFileConstants.ACCESS_PUBLIC
                : StorageFileConstants.ACCESS_PRIVATE;
    }
}
