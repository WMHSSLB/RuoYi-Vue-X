package com.ruoyi.common.utils.file;

import org.dromara.x.file.storage.core.constant.Constant;
import org.dromara.x.file.storage.core.upload.InitiateMultipartUploadPretreatment;
import org.dromara.x.file.storage.core.upload.UploadPretreatment;
import org.springframework.stereotype.Component;
import com.ruoyi.common.config.StorageFileUploadProperties;
import com.ruoyi.common.config.UploadSceneProperties;
import com.ruoyi.common.config.UploadSceneProperties.Scene;
import com.ruoyi.common.constant.StorageFileConstants;
import com.ruoyi.common.utils.StringUtils;

/**
 * 上传场景策略服务
 * <p>
 * 负责把 uploadScene 配置解析成上传流程可直接使用的大小、格式、路径、缩略图和 ACL 策略
 *
 * @author 955Kane
 */
@Component
public class StorageUploadScenePolicyService
{
    /**
     * 上传场景配置
     */
    private final UploadSceneProperties uploadSceneProperties;

    /**
     * 存储文件上传控制配置
     */
    private final StorageFileUploadProperties storageFileUploadProperties;

    /**
     * 构造上传场景策略服务
     *
     * @param uploadSceneProperties 上传场景配置
     * @param storageFileUploadProperties 存储文件上传控制配置
     */
    public StorageUploadScenePolicyService(UploadSceneProperties uploadSceneProperties,
            StorageFileUploadProperties storageFileUploadProperties)
    {
        this.uploadSceneProperties = uploadSceneProperties;
        this.storageFileUploadProperties = storageFileUploadProperties;
    }

    /**
     * 解析当前上传场景的最终策略
     *
     * @param uploadScene 可选上传场景编码
     * @param fallbackAllowedExtension 调用方兜底扩展名白名单
     * @param thumbnail 调用方兜底缩略图开关
     * @param thumbnailWidth 调用方兜底缩略图宽度
     * @param thumbnailHeight 调用方兜底缩略图高度
     * @param datePath 当前日期路径
     * @return 上传场景策略
     */
    public StorageUploadScenePolicy resolvePolicy(String uploadScene, String[] fallbackAllowedExtension,
            boolean thumbnail, Integer thumbnailWidth, Integer thumbnailHeight, String datePath)
    {
        // 场景解析会统一处理必传校验和未知场景校验
        Scene scene = getScene(uploadScene);
        // 场景配置优先于方法入参，便于同一个通用接口按业务差异收敛规则
        String[] allowedExtension = getAllowedExtension(scene, fallbackAllowedExtension);
        // 文件大小也由场景优先控制，未配置时保留若依默认 50MB
        long maxSize = getMaxSize(scene);
        // 缩略图开关允许业务接口传兜底值，也允许场景配置覆盖
        boolean actualThumbnail = scene != null && scene.getThumbnail() != null ? scene.getThumbnail() : thumbnail;
        // 缩略图宽度优先使用场景配置
        Integer actualThumbnailWidth = scene != null && scene.getThumbnailWidth() != null ? scene.getThumbnailWidth()
                : thumbnailWidth;
        // 缩略图高度优先使用场景配置
        Integer actualThumbnailHeight = scene != null && scene.getThumbnailHeight() != null ? scene.getThumbnailHeight()
                : thumbnailHeight;
        // 缩略图后缀按场景解析，未配置时交给 x-file-storage 全局默认值
        String actualThumbnailSuffix = getThumbnailSuffix(scene);
        // 存储路径由访问级别、场景目录和日期目录组成
        String storagePath = buildStoragePath(scene, uploadScene, datePath);
        // 访问级别后续用于设置对象 ACL，也会通过 path 落到文件记录里
        String access = getAccess(scene);
        return new StorageUploadScenePolicy(scene != null, uploadScene, allowedExtension, maxSize, actualThumbnail,
                actualThumbnailWidth, actualThumbnailHeight, actualThumbnailSuffix, storagePath, access);
    }

    /**
     * 解析单次批量上传最大文件数量
     *
     * @param uploadScene 可选上传场景编码
     * @param customMaxBatchUploadCount 代码指定的最大文件数量
     * @return 单次批量上传最大文件数量
     */
    public int resolveMaxBatchUploadCount(String uploadScene, Integer customMaxBatchUploadCount)
    {
        // 先读取场景，这一步会顺带校验 require-upload-scene 和场景存在性
        Scene scene = getScene(uploadScene);
        return getMaxBatchUploadCount(scene, customMaxBatchUploadCount);
    }

    /**
     * 按上传场景设置普通上传对象访问权限
     *
     * @param upload 当前上传预处理对象
     * @param policy 上传场景策略
     */
    public void applyAcl(UploadPretreatment upload, StorageUploadScenePolicy policy)
    {
        // 未声明场景时保留平台默认 ACL，兼容历史上传入口
        if (policy == null || !policy.isSceneMatched())
        {
            return;
        }
        // 部分平台不支持对象 ACL，关闭抛错可以保持多平台上传入口通用
        upload.setNotSupportAclThrowException(false);
        // public 场景需要匿名可读，否则私有 Bucket 会返回 AccessDenied
        if (StorageFileConstants.ACCESS_PUBLIC.equals(policy.getAccess()))
        {
            upload.setAcl(Constant.ACL.PUBLIC_READ);
            return;
        }
        // 未配置或非 public 场景统一按私有处理，避免业务附件被误公开
        upload.setAcl(Constant.ACL.PRIVATE);
    }

    /**
     * 按上传场景设置分片上传对象访问权限
     *
     * @param upload 当前分片上传初始化预处理对象
     * @param policy 上传场景策略
     */
    public void applyMultipartAcl(InitiateMultipartUploadPretreatment upload, StorageUploadScenePolicy policy)
    {
        // 未声明场景时保留平台默认 ACL，兼容历史分片上传入口
        if (policy == null || !policy.isSceneMatched())
        {
            return;
        }
        // 部分平台不支持对象 ACL，关闭抛错可以避免影响分片上传初始化
        upload.setNotSupportAclThrowException(false);
        // public 场景的分片文件合并后需要匿名可读
        if (StorageFileConstants.ACCESS_PUBLIC.equals(policy.getAccess()))
        {
            upload.setFileAcl(Constant.ACL.PUBLIC_READ);
            return;
        }
        // private 场景显式设置私有，避免平台默认公开导致业务附件泄露
        upload.setFileAcl(Constant.ACL.PRIVATE);
    }

    /**
     * 根据上传场景编码获取场景配置
     *
     * @param uploadScene 上传场景编码
     * @return 上传场景配置
     */
    private Scene getScene(String uploadScene)
    {
        // 生产环境可开启强制场景，避免新业务误走默认大小、格式和访问策略
        if (storageFileUploadProperties.isRequireUploadScene() && StringUtils.isBlank(uploadScene))
        {
            throw new IllegalArgumentException("上传场景不能为空");
        }
        // 没有传场景时不启用场景规则，保持兼容旧上传入口
        if (StringUtils.isBlank(uploadScene))
        {
            return null;
        }
        // 场景不存在时直接拒绝，避免前端传错后回退到更宽松的默认规则
        Scene scene = uploadSceneProperties.getScene(uploadScene);
        if (scene == null)
        {
            throw new IllegalArgumentException("上传场景不存在：" + uploadScene);
        }
        return scene;
    }

    /**
     * 获取当前上传允许的扩展名
     *
     * @param scene 上传场景配置
     * @param defaultAllowedExtension 默认扩展名白名单
     * @return 实际扩展名白名单
     */
    private String[] getAllowedExtension(Scene scene, String[] defaultAllowedExtension)
    {
        // 场景没有配置扩展名时使用调用方默认白名单
        if (scene == null || scene.getAllowedExtension() == null || scene.getAllowedExtension().isEmpty())
        {
            return defaultAllowedExtension;
        }
        // 将配置列表转换为若依校验工具使用的字符串数组
        return scene.getAllowedExtension().toArray(new String[0]);
    }

    /**
     * 获取当前上传最大大小
     *
     * @param scene 上传场景配置
     * @return 最大上传大小，单位字节
     */
    private long getMaxSize(Scene scene)
    {
        // 场景没有配置大小时使用若依默认 50MB
        if (scene == null || scene.getMaxSize() == null)
        {
            return FileUploadUtils.DEFAULT_MAX_SIZE;
        }
        // DataSize 会把 5MB、10MB 等配置换算成字节
        return scene.getMaxSize().toBytes();
    }

    /**
     * 获取单次批量上传最大文件数量
     *
     * @param scene 上传场景配置
     * @param customMaxBatchUploadCount 代码指定的最大文件数量
     * @return 单次批量上传最大文件数量
     */
    private int getMaxBatchUploadCount(Scene scene, Integer customMaxBatchUploadCount)
    {
        // 业务专用接口传入代码级限制时优先使用，适合后端根据业务状态动态限制
        if (customMaxBatchUploadCount != null && customMaxBatchUploadCount > 0)
        {
            return customMaxBatchUploadCount;
        }
        // 上传场景配置了批量数量时优先使用场景值，便于 A 业务 5 个、B 业务 10 个
        if (scene != null && scene.getMaxBatchUploadCount() != null && scene.getMaxBatchUploadCount() > 0)
        {
            return scene.getMaxBatchUploadCount();
        }
        // 场景未配置时回退到全局批量上传数量
        return storageFileUploadProperties.getEffectiveMaxBatchUploadCount();
    }

    /**
     * 构建场景化存储路径
     *
     * @param scene 上传场景配置
     * @param uploadScene 上传场景编码
     * @param datePath 日期路径
     * @return X File Storage setPath 使用的相对路径
     */
    private String buildStoragePath(Scene scene, String uploadScene, String datePath)
    {
        // 访问级别作为路径第一层，便于公开文件和私有文件按路径区分
        String access = getAccess(scene);
        // 场景路径作为第二层，方便按业务分类管理文件
        String pathPrefix = getPathPrefix(scene, uploadScene);
        return access + "/" + pathPrefix + "/" + datePath + "/";
    }

    /**
     * 获取上传场景访问级别
     *
     * @param scene 上传场景配置
     * @return public 或 private
     */
    private String getAccess(Scene scene)
    {
        // 未配置场景或未配置访问级别时默认私有，避免新业务文件误公开
        if (scene == null || StringUtils.isBlank(scene.getAccess()))
        {
            return StorageFileConstants.ACCESS_PRIVATE;
        }
        // 只接受 public，其它值一律按 private 处理
        return StorageFileConstants.ACCESS_PUBLIC.equalsIgnoreCase(scene.getAccess()) ? StorageFileConstants.ACCESS_PUBLIC
                : StorageFileConstants.ACCESS_PRIVATE;
    }

    /**
     * 获取上传场景路径前缀
     *
     * @param scene 上传场景配置
     * @param uploadScene 上传场景编码
     * @return 已清洗的相对路径前缀
     */
    private String getPathPrefix(Scene scene, String uploadScene)
    {
        // 场景 path-prefix 优先，未配置时使用 uploadScene，再没有时落到 common
        String pathPrefix = scene != null && StringUtils.isNotBlank(scene.getPathPrefix()) ? scene.getPathPrefix()
                : uploadScene;
        if (StringUtils.isBlank(pathPrefix))
        {
            pathPrefix = "common";
        }
        return normalizePathPrefix(pathPrefix);
    }

    /**
     * 获取上传场景缩略图后缀
     *
     * @param scene 上传场景配置
     * @return 缩略图后缀，未配置时返回 null 使用全局默认
     */
    private String getThumbnailSuffix(Scene scene)
    {
        // 场景未配置缩略图后缀时不覆盖 X File Storage 全局 thumbnail-suffix
        if (scene == null || StringUtils.isBlank(scene.getThumbnailSuffix()))
        {
            return null;
        }
        // 后缀必须以点号开头，避免生成没有扩展名语义的缩略图文件名
        String thumbnailSuffix = scene.getThumbnailSuffix().trim();
        if (!thumbnailSuffix.startsWith("."))
        {
            throw new IllegalArgumentException("缩略图后缀必须以 . 开头");
        }
        return thumbnailSuffix;
    }

    /**
     * 清洗路径前缀
     *
     * @param pathPrefix 原始路径前缀
     * @return 清洗后的路径前缀
     */
    private String normalizePathPrefix(String pathPrefix)
    {
        // 统一反斜杠，避免 Windows 路径分隔符进入对象存储 key
        String normalized = pathPrefix.replace("\\", "/").trim();
        // 去掉开头斜杠，避免变成绝对路径
        while (normalized.startsWith("/"))
        {
            normalized = normalized.substring(1);
        }
        // 去掉结尾斜杠，避免拼接时出现双斜杠
        while (normalized.endsWith("/"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (StringUtils.isBlank(normalized) || normalized.contains(".."))
        {
            throw new IllegalArgumentException("上传场景路径前缀不合法");
        }
        return normalized;
    }

    /**
     * 上传场景最终策略
     */
    public static class StorageUploadScenePolicy
    {
        /**
         * 是否匹配到上传场景
         */
        private final boolean sceneMatched;

        /**
         * 上传场景编码
         */
        private final String uploadScene;

        /**
         * 实际扩展名白名单
         */
        private final String[] allowedExtension;

        /**
         * 最大上传大小，单位字节
         */
        private final long maxSize;

        /**
         * 是否生成缩略图
         */
        private final boolean thumbnail;

        /**
         * 缩略图宽度
         */
        private final Integer thumbnailWidth;

        /**
         * 缩略图高度
         */
        private final Integer thumbnailHeight;

        /**
         * 缩略图后缀
         */
        private final String thumbnailSuffix;

        /**
         * 实际存储路径
         */
        private final String storagePath;

        /**
         * 访问级别
         */
        private final String access;

        public StorageUploadScenePolicy(boolean sceneMatched, String uploadScene, String[] allowedExtension, long maxSize,
                boolean thumbnail, Integer thumbnailWidth, Integer thumbnailHeight, String thumbnailSuffix,
                String storagePath, String access)
        {
            this.sceneMatched = sceneMatched;
            this.uploadScene = uploadScene;
            this.allowedExtension = allowedExtension;
            this.maxSize = maxSize;
            this.thumbnail = thumbnail;
            this.thumbnailWidth = thumbnailWidth;
            this.thumbnailHeight = thumbnailHeight;
            this.thumbnailSuffix = thumbnailSuffix;
            this.storagePath = storagePath;
            this.access = access;
        }

        public boolean isSceneMatched()
        {
            return sceneMatched;
        }

        public String getUploadScene()
        {
            return uploadScene;
        }

        public String[] getAllowedExtension()
        {
            return allowedExtension;
        }

        public long getMaxSize()
        {
            return maxSize;
        }

        public boolean isThumbnail()
        {
            return thumbnail;
        }

        public Integer getThumbnailWidth()
        {
            return thumbnailWidth;
        }

        public Integer getThumbnailHeight()
        {
            return thumbnailHeight;
        }

        public String getThumbnailSuffix()
        {
            return thumbnailSuffix;
        }

        public String getStoragePath()
        {
            return storagePath;
        }

        public String getAccess()
        {
            return access;
        }
    }
}
