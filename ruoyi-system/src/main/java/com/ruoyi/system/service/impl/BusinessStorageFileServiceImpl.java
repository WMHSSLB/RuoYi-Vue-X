package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.domain.BaseEntity;
import com.ruoyi.common.core.domain.model.StorageFileUploadRespDTO;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.service.BusinessStorageFileField;
import com.ruoyi.system.service.IBusinessStorageFileService;
import com.ruoyi.system.service.ISysStorageFileService;

/**
 * 业务文件生命周期服务实现
 *
 * @author 955Kane
 */
@Service
public class BusinessStorageFileServiceImpl implements IBusinessStorageFileService
{
    @Autowired
    private ISysStorageFileService storageFileService;

    /**
     * 填充单条业务数据的文件回显列表
     *
     * @param business 业务数据
     * @param fields 业务文件字段定义
     * @param <T> 业务实体类型
     */
    @Override
    public <T> void fillStorageFileList(T business, Collection<BusinessStorageFileField<T>> fields)
    {
        fillStorageFileListByFields(business, fields);
    }

    /**
     * 按显式字段定义填充单条业务数据的文件回显列表
     *
     * @param business 业务数据
     * @param fields 业务文件字段定义
     * @param <T> 业务实体类型
     */
    private <T> void fillStorageFileListByFields(T business, Collection<BusinessStorageFileField<T>> fields)
    {
        if (business == null || fields == null || fields.isEmpty())
        {
            return;
        }
        for (BusinessStorageFileField<T> field : fields)
        {
            if (field == null || field.getFileIdsGetter() == null || field.getFileListSetter() == null)
            {
                continue;
            }
            // 当前业务接口已经完成权限校验，这里只把文件ID转换为前端回显 DTO
            Collection<Long> fileIds = parseFileIds(field.getFileIdsGetter().apply(business));
            List<StorageFileUploadRespDTO> fileList = storageFileService.buildAuthorizedFileListByIds(fileIds);
            field.getFileListSetter().accept(business, fileList);
        }
    }

    /**
     * 填充业务列表的文件回显列表
     *
     * @param businesses 业务数据列表
     * @param fields 业务文件字段定义
     * @param <T> 业务实体类型
     */
    @Override
    public <T> void fillStorageFileList(Collection<T> businesses, Collection<BusinessStorageFileField<T>> fields)
    {
        fillStorageFileListBatchByFields(businesses, fields);
    }

    /**
     * 按显式字段定义填充业务列表的文件回显列表
     *
     * @param businesses 业务数据列表
     * @param fields 业务文件字段定义
     * @param <T> 业务实体类型
     */
    private <T> void fillStorageFileListBatchByFields(Collection<T> businesses,
            Collection<BusinessStorageFileField<T>> fields)
    {
        if (businesses == null || businesses.isEmpty() || fields == null || fields.isEmpty())
        {
            return;
        }
        Map<Long, StorageFileUploadRespDTO> fileInfoMap = buildAuthorizedFileInfoMap(businesses, fields);
        for (T business : businesses)
        {
            if (business == null)
            {
                continue;
            }
            for (BusinessStorageFileField<T> field : fields)
            {
                if (field == null || field.getFileIdsGetter() == null || field.getFileListSetter() == null)
                {
                    continue;
                }
                // 批量查询结果按当前业务字段原始顺序回填，避免列表回显顺序和业务字段保存顺序不一致
                field.getFileListSetter().accept(business,
                        buildFileListByIds(parseFileIds(field.getFileIdsGetter().apply(business)), fileInfoMap));
            }
        }
    }

    /**
     * 绑定业务数据中的文件ID
     *
     * @param business 业务数据
     * @param bizId 业务ID
     * @param fields 业务文件字段定义
     * @param updateBy 更新者
     * @param <T> 业务实体类型
     * @return 影响行数
     */
    @Override
    public <T> int bindStorageFiles(T business, String bizId, Collection<BusinessStorageFileField<T>> fields,
            String updateBy)
    {
        if (business == null || StringUtils.isBlank(bizId) || fields == null || fields.isEmpty())
        {
            return 0;
        }
        int rows = 0;
        for (BusinessStorageFileField<T> field : fields)
        {
            if (field == null || field.getFileIdsGetter() == null)
            {
                continue;
            }
            // 新增业务保存成功后，将每个字段里的临时文件绑定到当前业务
            Collection<Long> fileIds = parseFileIds(field.getFileIdsGetter().apply(business));
            rows += storageFileService.bindStorageFilesByIds(fileIds, field.getBizType(), bizId, updateBy);
        }
        return rows;
    }

    /**
     * 同步业务修改前后的文件ID
     *
     * @param newBusiness 修改后的业务数据
     * @param oldBusiness 修改前的业务数据
     * @param bizId 业务ID
     * @param fields 业务文件字段定义
     * @param updateBy 更新者
     * @param <T> 业务实体类型
     * @return 影响行数
     */
    @Override
    public <T> int syncStorageFiles(T newBusiness, T oldBusiness, String bizId,
            Collection<BusinessStorageFileField<T>> fields, String updateBy)
    {
        if (newBusiness == null || oldBusiness == null || StringUtils.isBlank(bizId) || fields == null || fields.isEmpty())
        {
            return 0;
        }
        int rows = 0;
        for (BusinessStorageFileField<T> field : fields)
        {
            if (field == null || field.getFileIdsGetter() == null)
            {
                continue;
            }
            // 修改业务时按字段分别计算新旧文件差集，旧集合中被移除的文件会进入待删除状态
            Collection<Long> newFileIds = parseFileIds(field.getFileIdsGetter().apply(newBusiness));
            Collection<Long> oldFileIds = parseFileIds(field.getFileIdsGetter().apply(oldBusiness));
            rows += storageFileService.syncBizStorageFilesByIds(newFileIds, oldFileIds, field.getBizType(), bizId, updateBy);
        }
        return rows;
    }

    /**
     * 按业务ID集合标记业务下所有文件为待删除
     *
     * @param bizIds 业务ID集合
     * @param fields 业务文件字段定义
     * @param updateBy 更新者
     * @param <T> 业务实体类型
     * @return 影响行数
     */
    private <T> int markStorageFilesWaitDeleteByBiz(Collection<String> bizIds,
            Collection<BusinessStorageFileField<T>> fields, String updateBy)
    {
        if (bizIds == null || bizIds.isEmpty() || fields == null || fields.isEmpty())
        {
            return 0;
        }
        List<String> bizTypes = collectBizTypes(fields);
        if (bizTypes.isEmpty())
        {
            return 0;
        }
        int rows = 0;
        for (String bizId : bizIds)
        {
            if (StringUtils.isNotBlank(bizId))
            {
                // 删除整条业务数据时，按业务ID和该业务声明的全部文件类型统一标记待删除
                rows += storageFileService.markStorageFilesWaitDeleteByBiz(bizTypes, bizId, updateBy);
            }
        }
        return rows;
    }

    /**
     * 按业务ID数组标记业务下所有文件为待删除
     *
     * @param bizIds 业务ID数组
     * @param fields 业务文件字段定义
     * @param updateBy 更新者
     * @param <T> 业务实体类型
     * @return 影响行数
     */
    @Override
    public <T> int markStorageFilesWaitDeleteByBiz(Object[] bizIds, Collection<BusinessStorageFileField<T>> fields,
            String updateBy)
    {
        return markStorageFilesWaitDeleteByBiz(toBizIdList(bizIds), fields, updateBy);
    }

    /**
     * 解析逗号分隔的文件ID
     *
     * @param fileIds 文件ID字符串
     * @return 文件ID集合
     */
    @Override
    public Collection<Long> parseFileIds(String fileIds)
    {
        List<Long> result = new ArrayList<Long>();
        if (StringUtils.isBlank(fileIds))
        {
            return result;
        }
        Set<Long> exists = new HashSet<Long>();
        String[] values = fileIds.split(",");
        for (String value : values)
        {
            Long fileId = parseFileId(value);
            if (fileId != null && exists.add(fileId))
            {
                // 去重后保留有效文件ID，避免同一个文件重复绑定或重复标记待删除
                result.add(fileId);
            }
        }
        return result;
    }

    /**
     * 获取当前操作者
     *
     * @param entity 业务实体
     * @return 操作者用户名
     */
    @Override
    public String getOperator(BaseEntity entity)
    {
        try
        {
            // 优先使用若依当前登录上下文，避免请求体伪造 createBy 或 updateBy 影响文件归属校验
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            if (entity != null && StringUtils.isNotBlank(entity.getUpdateBy()))
            {
                return entity.getUpdateBy();
            }
            if (entity != null && StringUtils.isNotBlank(entity.getCreateBy()))
            {
                return entity.getCreateBy();
            }
            return "";
        }
    }

    /**
     * 解析单个文件ID
     *
     * @param value 文件ID文本
     * @return 文件ID
     */
    private Long parseFileId(String value)
    {
        if (StringUtils.isBlank(value))
        {
            return null;
        }
        try
        {
            Long fileId = Long.valueOf(value.trim());
            // 小于等于 0 的值不是有效文件主键，直接忽略
            return fileId.longValue() > 0 ? fileId : null;
        }
        catch (NumberFormatException e)
        {
            // 兼容历史 URL、富文本 HTML 或其它非 fileId 内容，文件资产同步只处理数字ID
            return null;
        }
    }

    /**
     * 转换业务ID数组
     *
     * @param bizIds 业务ID数组
     * @return 业务ID集合
     */
    private List<String> toBizIdList(Object[] bizIds)
    {
        List<String> result = new ArrayList<String>();
        if (bizIds == null || bizIds.length == 0)
        {
            return result;
        }
        for (Object bizId : bizIds)
        {
            if (bizId != null && StringUtils.isNotBlank(String.valueOf(bizId)))
            {
                // 文件表中的 bizId 统一按字符串保存，兼容 Long、String 等业务主键类型
                result.add(String.valueOf(bizId));
            }
        }
        return result;
    }

    /**
     * 批量构造文件授权信息 Map
     *
     * @param businesses 业务数据列表
     * @param fields 业务文件字段定义
     * @param <T> 业务实体类型
     * @return 文件授权信息 Map
     */
    private <T> Map<Long, StorageFileUploadRespDTO> buildAuthorizedFileInfoMap(Collection<T> businesses,
            Collection<BusinessStorageFileField<T>> fields)
    {
        Collection<Long> allFileIds = collectFileIds(businesses, fields);
        List<StorageFileUploadRespDTO> fileList = storageFileService.buildAuthorizedFileListByIds(allFileIds);
        Map<Long, StorageFileUploadRespDTO> fileInfoMap = new LinkedHashMap<Long, StorageFileUploadRespDTO>();
        for (StorageFileUploadRespDTO fileInfo : fileList)
        {
            if (fileInfo != null && fileInfo.getFileId() != null)
            {
                // 同一个 fileId 只保留一份授权 DTO，后续多个业务字段可以复用同一对象
                fileInfoMap.put(fileInfo.getFileId(), fileInfo);
            }
        }
        return fileInfoMap;
    }

    /**
     * 收集业务列表中的全部文件ID
     *
     * @param businesses 业务数据列表
     * @param fields 业务文件字段定义
     * @param <T> 业务实体类型
     * @return 文件ID集合
     */
    private <T> Collection<Long> collectFileIds(Collection<T> businesses, Collection<BusinessStorageFileField<T>> fields)
    {
        List<Long> fileIds = new ArrayList<Long>();
        Set<Long> exists = new HashSet<Long>();
        if (businesses == null || fields == null)
        {
            return fileIds;
        }
        for (T business : businesses)
        {
            if (business == null)
            {
                continue;
            }
            for (BusinessStorageFileField<T> field : fields)
            {
                if (field == null || field.getFileIdsGetter() == null)
                {
                    continue;
                }
                for (Long fileId : parseFileIds(field.getFileIdsGetter().apply(business)))
                {
                    if (exists.add(fileId))
                    {
                        // 跨字段去重后批量查询，避免列表接口产生 N+1 文件查询
                        fileIds.add(fileId);
                    }
                }
            }
        }
        return fileIds;
    }

    /**
     * 按文件ID顺序构造文件回显列表
     *
     * @param fileIds 文件ID集合
     * @param fileInfoMap 文件授权信息 Map
     * @return 文件回显列表
     */
    private List<StorageFileUploadRespDTO> buildFileListByIds(Collection<Long> fileIds,
            Map<Long, StorageFileUploadRespDTO> fileInfoMap)
    {
        List<StorageFileUploadRespDTO> fileList = new ArrayList<StorageFileUploadRespDTO>();
        if (fileIds == null || fileIds.isEmpty() || fileInfoMap == null || fileInfoMap.isEmpty())
        {
            return fileList;
        }
        for (Long fileId : fileIds)
        {
            StorageFileUploadRespDTO fileInfo = fileInfoMap.get(fileId);
            if (fileInfo != null)
            {
                // 按业务字段保存的 fileId 顺序回填，保证前端多文件展示顺序稳定
                fileList.add(fileInfo);
            }
        }
        return fileList;
    }

    /**
     * 收集业务文件类型
     *
     * @param fields 业务文件字段定义
     * @param <T> 业务实体类型
     * @return 文件业务类型集合
     */
    private <T> List<String> collectBizTypes(Collection<BusinessStorageFileField<T>> fields)
    {
        List<String> bizTypes = new ArrayList<String>();
        Set<String> exists = new HashSet<String>();
        for (BusinessStorageFileField<T> field : fields)
        {
            if (field != null && StringUtils.isNotBlank(field.getBizType()) && exists.add(field.getBizType()))
            {
                // 同一个业务类型只保留一次，避免生成重复的 in 条件
                bizTypes.add(field.getBizType());
            }
        }
        return bizTypes;
    }
}
