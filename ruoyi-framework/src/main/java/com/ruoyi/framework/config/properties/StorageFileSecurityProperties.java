package com.ruoyi.framework.config.properties;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 存储文件安全配置
 * <p>
 * 用于控制哪些 X File Storage 访问路径允许匿名访问
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "ruoyi.storage-file")
public class StorageFileSecurityProperties
{
    /**
     * 公开文件访问路径
     */
    private List<String> publicPathPatterns = new ArrayList<String>();

    public List<String> getPublicPathPatterns()
    {
        return publicPathPatterns;
    }

    public void setPublicPathPatterns(List<String> publicPathPatterns)
    {
        this.publicPathPatterns = publicPathPatterns == null ? new ArrayList<String>() : publicPathPatterns;
    }
}
