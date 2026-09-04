package com.ruoyi.common.utils.file;

/**
 * MIME 类型和文件扩展名白名单常量
 * <p>
 * {@link FileUploadUtils} 和 {@link XFileStorageUtils} 会使用这些扩展名数组做上传校验
 * 如果要新增允许上传的文件类型，应先在这里扩展白名单，再决定哪些接口使用新的白名单
 *
 * @author ruoyi
 */
public class MimeTypeUtils
{
    /**
     * PNG 图片 MIME 类型
     */
    public static final String IMAGE_PNG = "image/png";

    /**
     * JPG 图片常见 MIME 类型
     */
    public static final String IMAGE_JPG = "image/jpg";

    /**
     * JPEG 图片标准 MIME 类型
     */
    public static final String IMAGE_JPEG = "image/jpeg";

    /**
     * BMP 图片 MIME 类型
     */
    public static final String IMAGE_BMP = "image/bmp";

    /**
     * GIF 图片 MIME 类型
     */
    public static final String IMAGE_GIF = "image/gif";

    /**
     * 图片上传允许的扩展名，头像上传等图片专用接口使用该白名单
     */
    public static final String[] IMAGE_EXTENSION = { "bmp", "gif", "jpg", "jpeg", "png" };

    /**
     * Flash 相关扩展名，保留若依原有兼容逻辑
     */
    public static final String[] FLASH_EXTENSION = { "swf", "flv" };

    /**
     * 音视频媒体扩展名，供媒体类上传校验使用
     */
    public static final String[] MEDIA_EXTENSION = { "swf", "flv", "mp3", "wav", "wma", "wmv", "mid", "avi", "mpg",
            "asf", "rm", "rmvb" };

    /**
     * 视频扩展名，供视频类上传校验使用
     */
    public static final String[] VIDEO_EXTENSION = { "mp4", "avi", "rmvb" };

    /**
     * 通用上传接口默认允许的扩展名
     * <p>
     * 该列表会同时用于通用上传和下载校验，应保持谨慎，不建议加入可执行脚本类文件
     */
    public static final String[] DEFAULT_ALLOWED_EXTENSION = {
            // 图片
            "bmp", "gif", "jpg", "jpeg", "png",
            // Office 文档和文本
            "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt",
            // 压缩文件
            "rar", "zip", "gz", "bz2",
            // 视频格式
            "mp4", "avi", "rmvb",
            // PDF 文档
            "pdf" };

    /**
     * 将图片 Content-Type 映射为默认扩展名
     * <p>
     * 当 MultipartFile 的原始文件名没有扩展名时，上传工具会尝试根据 Content-Type 兜底
     *
     * @param prefix 请求中的 Content-Type
     * @return 不带点号的扩展名，不支持时返回空字符串
     */
    public static String getExtension(String prefix)
    {
        // 只处理图片 Content-Type，未知类型返回空字符串
        switch (prefix)
        {
            case IMAGE_PNG:
                // image/png 对应 png 扩展名
                return "png";
            case IMAGE_JPG:
                // image/jpg 对应 jpg 扩展名
                return "jpg";
            case IMAGE_JPEG:
                // image/jpeg 对应 jpeg 扩展名
                return "jpeg";
            case IMAGE_BMP:
                // image/bmp 对应 bmp 扩展名
                return "bmp";
            case IMAGE_GIF:
                // image/gif 对应 gif 扩展名
                return "gif";
            default:
                // 非图片或未知 Content-Type 不做扩展名推断
                return "";
        }
    }
}
