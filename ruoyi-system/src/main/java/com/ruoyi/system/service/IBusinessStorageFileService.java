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
     * 根据实体字段注解填充单条业务数据的文件回显列表
     *
     * @param business 业务数据
     * @param <T> 业务实体类型
     */
    public <T> void fillStorageFileList(T business);

    /**
     * 根据实体字段注解填充业务列表的文件回显列表
     *
     * @param businesses 业务数据列表
     * @param <T> 业务实体类型
     */
    public <T> void fillStorageFileList(Collection<T> businesses);

    /**
     * 根据实体字段注解绑定业务数据中的文件ID
     *
     * @param business 业务数据
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @param <T> 业务实体类型
     * @return 影响行数
     */
    public <T> int bindStorageFiles(T business, String bizId, String updateBy);

    /**
     * 根据实体字段注解同步业务修改前后的文件ID
     *
     * @param newBusiness 修改后的业务数据
     * @param oldBusiness 修改前的业务数据
     * @param bizId 业务ID
     * @param updateBy 更新者
     * @param <T> 业务实体类型
     * @return 影响行数
     */
    public <T> int syncStorageFiles(T newBusiness, T oldBusiness, String bizId, String updateBy);

    /**
     * 根据实体字段注解按业务ID数组标记业务下所有文件为待删除
     *
     * @param bizIds 业务ID数组
     * @param businessClass 业务实体类型
     * @param updateBy 更新者
     * @return 影响行数
     */
    public int markStorageFilesWaitDeleteByBiz(Object[] bizIds, Class<?> businessClass, String updateBy);

    /**
     * 根据实体字段注解按业务ID集合标记业务下所有文件为待删除
     *
     * @param bizIds 业务ID集合
     * @param businessClass 业务实体类型
     * @param updateBy 更新者
     * @return 影响行数
     */
    public int markStorageFilesWaitDeleteByBiz(Collection<String> bizIds, Class<?> businessClass, String updateBy);

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
