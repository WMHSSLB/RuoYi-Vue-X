package com.ruoyi.system.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.StringUtils;

/**
 * 存储文件管理配置
 * <p>
 * 用于控制临时文件过期时间，以及不同业务类型旧文件进入待删除后的保留时间
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "ruoyi.storage-file")
public class StorageFileProperties
{
    /** 临时文件过期小时数 */
    private int tempExpireHours = 24;

    /** 默认待删除延迟小时数 */
    private int defaultDeleteDelayHours = 24;

    /** 业务类型对应的待删除延迟小时数 */
    private Map<String, Integer> deleteDelayHours = new HashMap<>();

    public int getTempExpireHours()
    {
        return tempExpireHours;
    }

    public void setTempExpireHours(int tempExpireHours)
    {
        this.tempExpireHours = tempExpireHours;
    }

    public int getDefaultDeleteDelayHours()
    {
        return defaultDeleteDelayHours;
    }

    public void setDefaultDeleteDelayHours(int defaultDeleteDelayHours)
    {
        this.defaultDeleteDelayHours = defaultDeleteDelayHours;
    }

    public Map<String, Integer> getDeleteDelayHours()
    {
        return deleteDelayHours;
    }

    public void setDeleteDelayHours(Map<String, Integer> deleteDelayHours)
    {
        this.deleteDelayHours = deleteDelayHours;
    }

    /**
     * 根据业务类型获取待删除延迟小时数
     *
     * @param bizType 业务类型
     * @return 待删除延迟小时数
     */
    public int getDeleteDelayHours(String bizType)
    {
        // 业务类型为空时直接使用默认值，适配通用文件删除场景
        if (StringUtils.isBlank(bizType) || deleteDelayHours == null)
        {
            return getPositiveOrDefault(defaultDeleteDelayHours);
        }
        // 业务单独配置了延迟时间时优先使用业务值
        Integer delayHours = deleteDelayHours.get(bizType);
        if (delayHours != null && delayHours > 0)
        {
            return delayHours;
        }
        // 未配置或配置非法时回退到默认延迟时间
        return getPositiveOrDefault(defaultDeleteDelayHours);
    }

    /**
     * 获取有效的临时文件过期小时数
     *
     * @return 临时文件过期小时数
     */
    public int getEffectiveTempExpireHours()
    {
        // 配置小于等于 0 时使用 24 小时兜底，避免误配置导致立即清理
        return getPositiveOrDefault(tempExpireHours);
    }

    /**
     * 获取正数配置或默认值
     *
     * @param value 配置值
     * @return 有效小时数
     */
    private int getPositiveOrDefault(int value)
    {
        // 所有小时数配置都要求是正数，非法值统一回退到 24 小时
        return value > 0 ? value : 24;
    }
}
