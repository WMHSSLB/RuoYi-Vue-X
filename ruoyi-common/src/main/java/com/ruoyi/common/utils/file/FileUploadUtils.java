package com.ruoyi.common.utils.file;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.apache.commons.io.FilenameUtils;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.exception.file.FileNameLengthLimitExceededException;
import com.ruoyi.common.exception.file.FileSizeLimitExceededException;
import com.ruoyi.common.exception.file.InvalidExtensionException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.common.utils.uuid.Seq;

/**
 * 本地文件上传工具类
 * <p>
 * 这是若依原有的本地磁盘上传实现。当前通用上传已经改由 {@link XFileStorageUtils}
 * 接入 X File Storage，但本类仍用于上传校验、文件命名、本地导入导出文件
 * 历史 /profile 路径处理等场景
 *
 * @author ruoyi
 */
public class FileUploadUtils
{
    /**
     * 默认最大上传大小：50MB
     */
    public static final long DEFAULT_MAX_SIZE = 50 * 1024 * 1024L;

    /**
     * 原始文件名允许的最大长度
     */
    public static final int DEFAULT_FILE_NAME_LENGTH = 100;

    /**
     * 默认本地上传根目录
     * <p>
     * 默认指向 ruoyi.profile；测试或自定义模块可以通过 {@link #setDefaultBaseDir(String)} 覆盖
     */
    private static String defaultBaseDir = RuoYiConfig.getProfile();

    /**
     * 设置默认本地上传根目录
     *
     * @param defaultBaseDir 绝对路径形式的上传根目录
     */
    public static void setDefaultBaseDir(String defaultBaseDir)
    {
        // 覆盖静态根目录，主要服务测试或特殊本地上传场景
        FileUploadUtils.defaultBaseDir = defaultBaseDir;
    }

    /**
     * 获取当前默认本地上传根目录
     *
     * @return 绝对路径形式的上传根目录
     */
    public static String getDefaultBaseDir()
    {
        // 返回当前生效的本地上传根目录
        return defaultBaseDir;
    }

    /**
     * 按默认本地根目录上传文件
     *
     * @param file 前端提交的 Multipart 文件
     * @return 若依资源路径，例如 /profile/upload/yyyy/MM/dd/xxx.png
     * @throws IOException 校验失败或本地写入失败时抛出
     */
    public static final String upload(MultipartFile file) throws IOException
    {
        try
        {
            // 未指定目录时使用 ruoyi.profile 作为本地上传根目录
            return upload(getDefaultBaseDir(), file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
        }
        catch (Exception e)
        {
            // 保持若依原方法签名，把校验异常包装为 IOException 向上抛出
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * 按指定本地根目录上传文件，并使用默认扩展名白名单
     *
     * @param baseDir 绝对路径形式的本地上传根目录
     * @param file 前端提交的 Multipart 文件
     * @return 若依资源路径
     * @throws IOException 校验失败或本地写入失败时抛出
     */
    public static final String upload(String baseDir, MultipartFile file) throws IOException
    {
        try
        {
            // 指定目录但未指定白名单时使用通用上传白名单
            return upload(baseDir, file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
        }
        catch (Exception e)
        {
            // 保持若依原方法签名，把校验异常包装为 IOException 向上抛出
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * 按指定扩展名白名单上传文件到本地磁盘
     *
     * @param baseDir 绝对路径形式的本地上传根目录
     * @param file 前端提交的 Multipart 文件
     * @param allowedExtension 允许的扩展名数组，不带点号
     * @return 若依资源路径
     * @throws FileSizeLimitExceededException 文件超过默认大小限制时抛出
     * @throws FileNameLengthLimitExceededException 原始文件名过长时抛出
     * @throws IOException 本地写入失败时抛出
     * @throws InvalidExtensionException 扩展名不在白名单内时抛出
     */
    public static final String upload(String baseDir, MultipartFile file, String[] allowedExtension)
            throws FileSizeLimitExceededException, IOException, FileNameLengthLimitExceededException,
            InvalidExtensionException
    {
        // 默认使用若依原命名策略，保留原文件名主体并追加序列号
        return upload(baseDir, file, allowedExtension, false);
    }

    /**
     * 上传文件到本地磁盘，并指定命名策略
     *
     * @param baseDir 绝对路径形式的本地上传根目录
     * @param file 前端提交的 Multipart 文件
     * @param allowedExtension 允许的扩展名数组，不带点号
     * @param useCustomNaming true 表示使用 UUID 文件名，false 表示保留原文件名主体并追加序列号
     * @return 若依资源路径
     * @throws FileSizeLimitExceededException 文件超过默认大小限制时抛出
     * @throws FileNameLengthLimitExceededException 原始文件名过长时抛出
     * @throws IOException 本地写入失败时抛出
     * @throws InvalidExtensionException 扩展名不在白名单内时抛出
     */
    public static final String upload(String baseDir, MultipartFile file, String[] allowedExtension, boolean useCustomNaming)
            throws FileSizeLimitExceededException, IOException, FileNameLengthLimitExceededException,
            InvalidExtensionException
    {
        // 原始文件名必须存在，并且长度不能超过系统限制
        int fileNameLength = Objects.requireNonNull(file.getOriginalFilename()).length();
        if (fileNameLength > FileUploadUtils.DEFAULT_FILE_NAME_LENGTH)
        {
            throw new FileNameLengthLimitExceededException(FileUploadUtils.DEFAULT_FILE_NAME_LENGTH);
        }

        // 先校验大小和扩展名，避免非法文件落盘
        assertAllowed(file, allowedExtension);

        // 根据调用方选择决定使用 UUID 命名还是若依原命名
        String fileName = useCustomNaming ? uuidFilename(file) : extractFilename(file);

        // 解析真实落盘路径，并确保父目录已经创建
        String absPath = getAbsoluteFile(baseDir, fileName).getAbsolutePath();
        // MultipartFile 写入本地文件系统
        file.transferTo(Paths.get(absPath));
        // 返回前端可保存的 /profile 资源路径
        return getPathFileName(baseDir, fileName);
    }

    /**
     * 生成默认本地上传文件名
     * <p>
     * 格式为 yyyy/MM/dd/原文件名主体_序列号.扩展名
     * 序列号用于降低重名概率，同时保留原文件名可读性
     *
     * @param file 前端提交的 Multipart 文件
     * @return 上传目录下的相对路径和文件名
     */
    public static final String extractFilename(MultipartFile file)
    {
        // 日期目录便于按天归档，序列号降低同名文件覆盖风险
        return StringUtils.format("{}/{}_{}.{}", DateUtils.datePath(), FilenameUtils.getBaseName(file.getOriginalFilename()), Seq.getId(Seq.uploadSeqType), getExtension(file));
    }

    /**
     * 生成 UUID 文件名
     * <p>
     * 格式为 yyyy/MM/dd/uuid.扩展名
     * XFileStorageUtils 也复用该方法，让远程对象名称和本地头像命名规则保持一致
     *
     * @param file 前端提交的 Multipart 文件
     * @return 上传目录下的相对路径和 UUID 文件名
     */
    public static final String uuidFilename(MultipartFile file)
    {
        // UUID 文件名避免暴露原始文件名，也降低对象存储中的重名风险
        return StringUtils.format("{}/{}.{}", DateUtils.datePath(), IdUtils.fastSimpleUUID(), getExtension(file));
    }

    /**
     * 将上传目录和相对文件名解析成本地绝对文件，并创建父目录
     *
     * @param uploadDir 绝对路径形式的上传根目录
     * @param fileName 上传根目录下的相对路径和文件名
     * @return 目标文件对象
     * @throws IOException 路径解析失败时抛出
     */
    public static final File getAbsoluteFile(String uploadDir, String fileName) throws IOException
    {
        // uploadDir 是绝对根目录，fileName 是日期目录加文件名
        File desc = new File(uploadDir + File.separator + fileName);

        if (!desc.exists())
        {
            // 目标文件不存在时先确保父目录存在
            if (!desc.getParentFile().exists())
            {
                // mkdirs 可一次性创建 yyyy/MM/dd 多级目录
                desc.getParentFile().mkdirs();
            }
        }
        return desc;
    }

    /**
     * 将本地上传文件路径转换成若依公开的 /profile 资源路径
     *
     * @param uploadDir 绝对路径形式的上传目录，通常是 ruoyi.profile 的子目录
     * @param fileName uploadDir 下的相对文件名
     * @return 前端可保存和访问的资源路径
     * @throws IOException 保留若依原方法签名
     */
    public static final String getPathFileName(String uploadDir, String fileName) throws IOException
    {
        // ruoyi.profile 后面的目录部分会作为 /profile 的二级路径
        int dirLastIndex = RuoYiConfig.getProfile().length() + 1;
        String currentDir = StringUtils.substring(uploadDir, dirLastIndex);
        // 拼成前端可访问和可保存的若依资源地址
        return Constants.RESOURCE_PREFIX + "/" + currentDir + "/" + fileName;
    }

    /**
     * 校验上传文件大小和扩展名
     *
     * @param file 前端提交的 Multipart 文件
     * @param allowedExtension 允许的扩展名数组，不带点号；为 null 时不校验扩展名
     * @throws FileSizeLimitExceededException 文件超过默认大小限制时抛出
     * @throws InvalidExtensionException 扩展名不在白名单内时抛出
     */
    public static final void assertAllowed(MultipartFile file, String[] allowedExtension)
            throws FileSizeLimitExceededException, InvalidExtensionException
    {
        // 未指定场景大小时使用若依默认 50MB 限制
        assertAllowed(file, allowedExtension, DEFAULT_MAX_SIZE);
    }

    /**
     * 校验上传文件大小和扩展名
     *
     * @param file 前端提交的 Multipart 文件
     * @param allowedExtension 允许的扩展名数组，不带点号；为 null 时不校验扩展名
     * @param maxSize 最大上传大小，单位字节
     * @throws FileSizeLimitExceededException 文件超过最大大小限制时抛出
     * @throws InvalidExtensionException 扩展名不在白名单内时抛出
     */
    public static final void assertAllowed(MultipartFile file, String[] allowedExtension, long maxSize)
            throws FileSizeLimitExceededException, InvalidExtensionException
    {
        // 超过当前场景允许大小时直接拒绝上传
        long size = file.getSize();
        if (size > maxSize)
        {
            throw new FileSizeLimitExceededException(toMb(maxSize));
        }

        // 扩展名优先来自原始文件名，没有扩展名时按 Content-Type 兜底
        String fileName = file.getOriginalFilename();
        String extension = getExtension(file);
        // allowedExtension 为 null 时仅校验大小，不校验扩展名
        if (allowedExtension != null && !isAllowedExtension(extension, allowedExtension))
        {
            // 图片白名单失败时抛出图片专用异常，前端提示更准确
            if (allowedExtension == MimeTypeUtils.IMAGE_EXTENSION)
            {
                throw new InvalidExtensionException.InvalidImageExtensionException(allowedExtension, extension,
                        fileName);
            }
            // Flash 白名单失败时保留若依原异常类型
            else if (allowedExtension == MimeTypeUtils.FLASH_EXTENSION)
            {
                throw new InvalidExtensionException.InvalidFlashExtensionException(allowedExtension, extension,
                        fileName);
            }
            // 媒体白名单失败时保留若依原异常类型
            else if (allowedExtension == MimeTypeUtils.MEDIA_EXTENSION)
            {
                throw new InvalidExtensionException.InvalidMediaExtensionException(allowedExtension, extension,
                        fileName);
            }
            // 视频白名单失败时保留若依原异常类型
            else if (allowedExtension == MimeTypeUtils.VIDEO_EXTENSION)
            {
                throw new InvalidExtensionException.InvalidVideoExtensionException(allowedExtension, extension,
                        fileName);
            }
            else
            {
                // 其它白名单使用通用非法扩展名异常
                throw new InvalidExtensionException(allowedExtension, extension, fileName);
            }
        }

        // 扩展名通过后只对图片做真实内容校验，PDF、音频、视频等普通文件不走 ImageIO
        assertRealImage(file, extension, allowedExtension);
    }

    /**
     * 将字节数转换为向上取整的 MB
     *
     * @param size 文件大小，单位字节
     * @return 文件大小，单位 MB
     */
    private static long toMb(long size)
    {
        // 异常消息按 MB 展示，非整 MB 时向上取整避免提示偏小
        return (size + 1024 * 1024 - 1) / 1024 / 1024;
    }

    /**
     * 判断扩展名是否在允许列表中
     *
     * @param extension 不带点号的扩展名
     * @param allowedExtension 允许的扩展名数组，不带点号
     * @return true 表示允许，不区分大小写
     */
    public static final boolean isAllowedExtension(String extension, String[] allowedExtension)
    {
        // 遍历白名单并忽略大小写，兼容 JPG、Png 等用户上传文件名
        for (String str : allowedExtension)
        {
            if (str.equalsIgnoreCase(extension))
            {
                // 命中任意一个白名单扩展名即可通过
                return true;
            }
        }
        // 遍历结束仍未命中则拒绝
        return false;
    }

    /**
     * 获取上传文件扩展名
     * <p>
     * 优先使用原始文件名中的扩展名；原始文件名没有扩展名时，再根据 Content-Type 兜底推断
     *
     * @param file 前端提交的 Multipart 文件
     * @return 不带点号的扩展名
     */
    public static final String getExtension(MultipartFile file)
    {
        // 先从原始文件名提取扩展名
        String extension = FilenameUtils.getExtension(file.getOriginalFilename());
        if (StringUtils.isEmpty(extension))
        {
            // 原始文件名没有扩展名时，根据 Content-Type 做图片类型兜底
            extension = MimeTypeUtils.getExtension(Objects.requireNonNull(file.getContentType()));
        }
        return extension;
    }

    /**
     * 校验图片文件真实内容
     *
     * @param file 前端提交的 Multipart 文件
     * @param extension 不带点号的扩展名
     * @param allowedExtension 允许的扩展名数组，不带点号
     * @throws InvalidExtensionException 图片扩展名与真实内容不匹配时抛出
     */
    private static void assertRealImage(MultipartFile file, String extension, String[] allowedExtension)
            throws InvalidExtensionException
    {
        // 非图片文件直接跳过真实图片校验，避免 PDF、音频、视频等文件被 ImageIO 误处理
        if (!shouldAssertRealImage(extension, allowedExtension))
        {
            return;
        }
        try
        {
            // ImageIO 能成功解析图片头时才认为是真实图片
            if (ImageIO.read(file.getInputStream()) == null)
            {
                // 扩展名已经通过白名单，但内容无法按图片解码时提示真实图片错误
                throw new InvalidExtensionException.InvalidImageContentException(allowedExtension, extension,
                        file.getOriginalFilename());
            }
        }
        catch (IOException e)
        {
            // 图片流读取异常按非法图片处理，避免损坏文件或伪造文件继续上传
            throw new InvalidExtensionException.InvalidImageContentException(allowedExtension, extension,
                    file.getOriginalFilename());
        }
    }

    /**
     * 判断当前文件是否需要做真实图片校验
     *
     * @param extension 不带点号的扩展名
     * @param allowedExtension 允许的扩展名数组，不带点号
     * @return true 表示需要按图片内容读取校验
     */
    private static boolean shouldAssertRealImage(String extension, String[] allowedExtension)
    {
        // 没有扩展名白名单时不做真实图片校验，保持原有宽松调用方式
        if (allowedExtension == null)
        {
            return false;
        }
        // 只有当前上传文件本身是图片扩展名时才需要读取图片头
        return isAllowedExtension(extension, MimeTypeUtils.IMAGE_EXTENSION);
    }

}
