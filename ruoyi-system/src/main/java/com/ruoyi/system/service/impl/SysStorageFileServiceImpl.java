package com.ruoyi.system.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.hash.HashInfo;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.StorageFileProperties;
import com.ruoyi.system.domain.SysStorageFile;
import com.ruoyi.system.domain.SysStorageFilePart;
import com.ruoyi.system.mapper.SysStorageFileMapper;
import com.ruoyi.system.mapper.SysStorageFilePartMapper;
import com.ruoyi.system.service.ISysStorageFileService;

/**
 * 存储文件记录 服务层实现
 * <p>
 * 该类同时实现 X File Storage 的 FileRecorder 接口，让上传记录真正落到 sys_storage_file
 *
 * @author ruoyi
 */
@Service
public class SysStorageFileServiceImpl implements ISysStorageFileService
{
    /** 临时文件 */
    private static final String STATUS_TEMP = "0";

    /** 已使用文件 */
    private static final String STATUS_USED = "1";

    /** 待删除文件 */
    private static final String STATUS_WAIT_DELETE = "2";

    /** 已删除文件 */
    private static final String STATUS_DELETED = "3";

    @Autowired
    private SysStorageFileMapper storageFileMapper;

    @Autowired
    private SysStorageFilePartMapper storageFilePartMapper;

    @Autowired
    private StorageFileProperties storageFileProperties;

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
        SysStorageFile exists = storageFileMapper.selectStorageFileByUrl(fileInfo.getUrl());
        SysStorageFile storageFile = toStorageFile(fileInfo);
        if (exists != null)
        {
            // 保留原主键并刷新最新元数据
            storageFile.setFileId(exists.getFileId());
            storageFile.setUpdateTime(DateUtils.getNowDate());
            return storageFileMapper.updateStorageFile(storageFile) > 0;
        }
        // 新上传文件先记录为临时状态，等业务保存成功后再绑定为已使用
        storageFile.setFileStatus(STATUS_TEMP);
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
        return toFileInfo(storageFileMapper.selectStorageFileByUrl(url));
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
     * 绑定文件到业务
     *
     * @param url 文件访问地址
     * @param bizType 业务类型
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @return 结果
     */
    @Override
    public int bindStorageFile(String url, String bizType, String bizId, String updateBy)
    {
        // 业务保存成功后把临时文件转为已使用，避免被临时文件清理任务误删
        if (StringUtils.isBlank(url))
        {
            return 0;
        }
        return storageFileMapper.bindStorageFile(url, bizType, bizId, updateBy);
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
        LocalDateTime deleteTime = LocalDateTime.now().plusHours(delayHours);
        return storageFileMapper.markStorageFileWaitDelete(url, deleteTime, updateBy);
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
        // 不同业务可以配置不同保留时间，例如头像短保留、合同附件长保留
        int delayHours = storageFileProperties.getDeleteDelayHours(bizType);
        return markStorageFileWaitDelete(url, delayHours, updateBy);
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
        return storageFileMapper.markStorageFileDeleted(url, updateBy);
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
        // 定时任务未传参数或参数非法时使用配置文件中的临时文件过期时间
        int expireHours = tempExpireHours <= 0 ? storageFileProperties.getEffectiveTempExpireHours() : tempExpireHours;
        // 临时文件按创建时间过期，待删除文件按 delete_time 到期
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime tempBefore = now.minusHours(expireHours);
        return storageFileMapper.selectCleanableStorageFiles(tempBefore, now);
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
}
