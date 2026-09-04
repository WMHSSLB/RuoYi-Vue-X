package com.ruoyi.common.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import com.ruoyi.common.utils.StringUtils;

/**
 * 上传场景配置
 * <p>
 * 用于让同一个通用上传接口根据不同业务场景使用不同大小、格式和缩略图规则
 *
 * @author ruoyi
 */
@Component
@ConfigurationProperties(prefix = "ruoyi")
public class UploadSceneProperties
{
    /** 场景配置集合 */
    private Map<String, Scene> uploadScenes = new HashMap<String, Scene>();

    public Map<String, Scene> getUploadScenes()
    {
        return uploadScenes;
    }

    public void setUploadScenes(Map<String, Scene> uploadScenes)
    {
        this.uploadScenes = uploadScenes;
    }

    /**
     * 根据场景编码获取上传场景
     *
     * @param scene 上传场景编码
     * @return 上传场景配置
     */
    public Scene getScene(String scene)
    {
        // 场景为空时不启用场景规则，保持通用上传原有默认行为
        if (StringUtils.isBlank(scene) || uploadScenes == null)
        {
            return null;
        }
        // 按配置键直接查找场景，调用方传 avatar、contract 等稳定编码
        return uploadScenes.get(scene);
    }

    /**
     * 上传场景规则
     */
    public static class Scene
    {
        /** 最大上传大小 */
        private DataSize maxSize;

        /** 允许上传的扩展名 */
        private List<String> allowedExtension;

        /** 是否生成缩略图 */
        private Boolean thumbnail;

        /** 缩略图宽度 */
        private Integer thumbnailWidth;

        /** 缩略图高度 */
        private Integer thumbnailHeight;

        /** 访问级别，public 表示公开访问，private 表示私有访问 */
        private String access;

        /** 场景路径前缀，例如 avatar、image、contract */
        private String pathPrefix;

        public DataSize getMaxSize()
        {
            return maxSize;
        }

        public void setMaxSize(DataSize maxSize)
        {
            this.maxSize = maxSize;
        }

        public List<String> getAllowedExtension()
        {
            return allowedExtension;
        }

        public void setAllowedExtension(List<String> allowedExtension)
        {
            this.allowedExtension = allowedExtension;
        }

        public Boolean getThumbnail()
        {
            return thumbnail;
        }

        public void setThumbnail(Boolean thumbnail)
        {
            this.thumbnail = thumbnail;
        }

        public Integer getThumbnailWidth()
        {
            return thumbnailWidth;
        }

        public void setThumbnailWidth(Integer thumbnailWidth)
        {
            this.thumbnailWidth = thumbnailWidth;
        }

        public Integer getThumbnailHeight()
        {
            return thumbnailHeight;
        }

        public void setThumbnailHeight(Integer thumbnailHeight)
        {
            this.thumbnailHeight = thumbnailHeight;
        }

        public String getAccess()
        {
            return access;
        }

        public void setAccess(String access)
        {
            this.access = access;
        }

        public String getPathPrefix()
        {
            return pathPrefix;
        }

        public void setPathPrefix(String pathPrefix)
        {
            this.pathPrefix = pathPrefix;
        }
    }
}
