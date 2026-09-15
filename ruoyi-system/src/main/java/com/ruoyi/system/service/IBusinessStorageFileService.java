package com.ruoyi.system.service;

import java.util.Collection;
import java.util.List;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 业务文件生命周期服务
 *
 * @author ruoyi
 */
public interface IBusinessStorageFileService
{
    /**
     * 根据显式文件字段映射填充单条业务数据的文件回显列表
     *
     * @param business 业务返回对象
     * @param fields 业务文件字段映射
     * @param <T> 业务返回对象类型
     */
    public <T> void fillStorageFileList(T business, Collection<BusinessStorageFileField<T>> fields);

    /**
     * 根据显式文件字段映射填充业务列表的文件回显列表
     *
     * @param businesses 业务返回对象列表
     * @param fields 业务文件字段映射
     * @param <T> 业务返回对象类型
     */
    public <T> void fillStorageFileList(Collection<T> businesses, Collection<BusinessStorageFileField<T>> fields);

    /**
     * 根据显式文件字段映射绑定业务数据中的文件ID
     *
     * @param business 业务数据
     * @param bizId 业务ID
     * @param fields 业务文件字段映射
     * @param updateBy 更新者
     * @param <T> 业务实体类型
     * @return 影响行数
     */
    public <T> int bindStorageFiles(T business, String bizId, Collection<BusinessStorageFileField<T>> fields,
            String updateBy);

    /**
     * 根据显式文件字段映射同步业务修改前后的文件ID
     *
     * @param newBusiness 修改后的业务数据
     * @param oldBusiness 修改前的业务数据
     * @param bizId 业务ID
     * @param fields 业务文件字段映射
     * @param updateBy 更新者
     * @param <T> 业务实体类型
     * @return 影响行数
     */
    public <T> int syncStorageFiles(T newBusiness, T oldBusiness, String bizId,
            Collection<BusinessStorageFileField<T>> fields, String updateBy);

    /**
     * 根据显式文件字段映射按业务ID数组标记业务下所有文件为待删除
     *
     * @param bizIds 业务ID数组
     * @param fields 业务文件字段映射
     * @param updateBy 更新者
     * @param <T> 业务实体类型
     * @return 影响行数
     */
    public <T> int markStorageFilesWaitDeleteByBiz(Object[] bizIds, Collection<BusinessStorageFileField<T>> fields,
            String updateBy);

    /**
     * 解析逗号分隔的文件ID
     *
     * @param fileIds 文件ID字符串
     * @return 文件ID集合
     */
    public Collection<Long> parseFileIds(String fileIds);

    /**
     * 获取当前操作者
     *
     * @param entity 业务实体
     * @return 操作者用户名
     */
    public String getOperator(BaseEntity entity);
}
