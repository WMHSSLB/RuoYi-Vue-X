package com.ruoyi.common.constant;

/**
 * 存储文件公共常量
 * <p>
 * 只收敛跨模块共享的固定协议值，具体业务场景编码仍由业务模块自行定义
 *
 * @author 955Kane
 */
public final class StorageFileConstants
{
    /**
     * 公开文件访问级别
     */
    public static final String ACCESS_PUBLIC = "public";

    /**
     * 私有文件访问级别
     */
    public static final String ACCESS_PRIVATE = "private";

    /**
     * 头像业务类型
     */
    public static final String BIZ_TYPE_AVATAR = "avatar";

    /**
     * 对象存储公开读 ACL
     */
    public static final String ACL_PUBLIC_READ = "public-read";

    /**
     * 存储文件管理下载权限
     */
    public static final String PERMISSION_STORAGE_DOWNLOAD = "system:storage:download";

    /**
     * 文件预览代理路径
     */
    public static final String PREVIEW_PATH = "/common/file/preview/";

    /**
     * 文件下载代理路径
     */
    public static final String DOWNLOAD_PATH = "/common/file/download/";

    private StorageFileConstants()
    {
    }
}
