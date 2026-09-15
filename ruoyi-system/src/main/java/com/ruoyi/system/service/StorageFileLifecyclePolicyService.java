package com.ruoyi.system.service;

import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.stereotype.Service;
import com.ruoyi.common.enums.StorageFileStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.config.StorageFileProperties;

/**
 * 存储文件生命周期策略服务
 * <p>
 * 统一维护临时、已使用、待删除、已删除之间的状态语义和时间计算规则
 *
 * @author 955Kane
 */
@Service
public class StorageFileLifecyclePolicyService
{
    /**
     * 存储文件管理配置
     */
    private final StorageFileProperties storageFileProperties;

    /**
     * 构造存储文件生命周期策略服务
     *
     * @param storageFileProperties 存储文件管理配置
     */
    public StorageFileLifecyclePolicyService(StorageFileProperties storageFileProperties)
    {
        this.storageFileProperties = storageFileProperties;
    }

    /**
     * 获取新上传文件的初始状态
     *
     * @return 临时文件状态编码
     */
    public String getInitialStatus()
    {
        // 文件刚上传时还没有业务归属，统一进入临时状态
        return StorageFileStatus.TEMP.getCode();
    }

    /**
     * 获取文件绑定业务后的状态
     *
     * @return 已使用文件状态编码
     */
    public String getUsedStatus()
    {
        // 业务保存成功并绑定文件后，临时文件转换为已使用状态
        return StorageFileStatus.USED.getCode();
    }

    /**
     * 获取待删除状态
     *
     * @return 待删除文件状态编码
     */
    public String getWaitDeleteStatus()
    {
        // 文件从业务移除后先进入待删除状态，由定时任务延迟物理删除
        return StorageFileStatus.WAIT_DELETE.getCode();
    }

    /**
     * 获取已删除状态
     *
     * @return 已删除文件状态编码
     */
    public String getDeletedStatus()
    {
        // 物理删除完成后保留数据库记录并标记为已删除
        return StorageFileStatus.DELETED.getCode();
    }

    /**
     * 判断文件是否处于待删除状态
     *
     * @param fileStatus 文件状态编码
     * @return true 表示待删除
     */
    public boolean isWaitDeleteStatus(String fileStatus)
    {
        return StorageFileStatus.WAIT_DELETE.getCode().equals(fileStatus);
    }

    /**
     * 校验文件是否允许重试删除
     *
     * @param fileStatus 文件状态编码
     */
    public void assertRetryDeleteAllowed(String fileStatus)
    {
        // 只有已经进入待删除队列的文件才允许重试物理删除
        if (!isWaitDeleteStatus(fileStatus))
        {
            throw new ServiceException("只有待删除文件可以重试删除");
        }
    }

    /**
     * 根据显式延迟小时数计算待删除时间
     *
     * @param delayHours 延迟删除小时数
     * @return 待删除时间
     */
    public LocalDateTime resolveDeleteTime(int delayHours)
    {
        // 调用方传入非法值时回退到默认延迟，避免文件被立即清理
        int effectiveDelayHours = delayHours > 0 ? delayHours : storageFileProperties.getDeleteDelayHours(null);
        return LocalDateTime.now().plusHours(effectiveDelayHours);
    }

    /**
     * 根据业务类型计算待删除时间
     *
     * @param bizType 业务类型
     * @return 待删除时间
     */
    public LocalDateTime resolveDeleteTime(String bizType)
    {
        // 业务类型为空或未配置时由配置类回退到默认延迟
        return LocalDateTime.now().plusHours(storageFileProperties.getDeleteDelayHours(bizType));
    }

    /**
     * 根据多个业务类型计算待删除时间
     *
     * @param bizTypes 业务类型集合
     * @return 待删除时间
     */
    public LocalDateTime resolveDeleteTime(Collection<String> bizTypes)
    {
        // 批量按业务删除时取最长保留时间，避免短保留场景提前删除仍需保留的附件
        int maxDelayHours = 0;
        if (bizTypes != null)
        {
            for (String bizType : bizTypes)
            {
                if (StringUtils.isNotBlank(bizType))
                {
                    maxDelayHours = Math.max(maxDelayHours, storageFileProperties.getDeleteDelayHours(bizType));
                }
            }
        }
        return resolveDeleteTime(maxDelayHours);
    }

    /**
     * 计算可清理文件查询时间范围
     *
     * @param tempExpireHours 临时文件过期小时数
     * @return 可清理文件查询时间范围
     */
    public CleanableStorageFileTimeRange resolveCleanableTimeRange(int tempExpireHours)
    {
        // 定时任务未传参数或参数非法时使用配置文件中的临时文件过期时间
        int expireHours = tempExpireHours <= 0 ? storageFileProperties.getEffectiveTempExpireHours() : tempExpireHours;
        LocalDateTime now = LocalDateTime.now();
        // 临时文件按 create_time 过期，待删除文件按 delete_time 到期
        return new CleanableStorageFileTimeRange(now.minusHours(expireHours), now);
    }

    /**
     * 可清理文件查询时间范围
     */
    public static class CleanableStorageFileTimeRange
    {
        /**
         * 临时文件过期时间点
         */
        private final LocalDateTime tempBefore;

        /**
         * 待删除文件到期时间点
         */
        private final LocalDateTime waitDeleteBefore;

        public CleanableStorageFileTimeRange(LocalDateTime tempBefore, LocalDateTime waitDeleteBefore)
        {
            this.tempBefore = tempBefore;
            this.waitDeleteBefore = waitDeleteBefore;
        }

        public LocalDateTime getTempBefore()
        {
            return tempBefore;
        }

        public LocalDateTime getWaitDeleteBefore()
        {
            return waitDeleteBefore;
        }
    }
}
