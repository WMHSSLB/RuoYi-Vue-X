package com.ruoyi.common.utils.file;

import java.io.File;
import org.apache.commons.lang3.StringUtils;

/**
 * 文件类型工具类
 * <p>
 * 该工具只根据文件扩展名或少量常见图片文件头判断类型，不做完整 MIME 检测
 * 接收用户上传文件时，仍需要结合上传白名单进行校验
 *
 * @author ruoyi
 */
public class FileTypeUtils
{
    /**
     * 从 File 对象中获取小写扩展名
     *
     * @param file 目标文件，为 null 时返回空字符串
     * @return 不带点号的扩展名，例如 txt
     */
    public static String getFileType(File file)
    {
        // 空文件对象直接返回空扩展名，避免调用 getName 触发空指针
        if (null == file)
        {
            return StringUtils.EMPTY;
        }
        // File 场景最终仍复用文件名解析逻辑
        return getFileType(file.getName());
    }

    /**
     * 从文件名或路径中获取小写扩展名
     * <p>
     * 例如 ruoyi.txt 返回 txt；没有点号的文件名返回空字符串，便于白名单校验拒绝
     *
     * @param fileName 文件名或资源路径
     * @return 不带点号的扩展名
     */
    public static String getFileType(String fileName)
    {
        // 取最后一个点号，兼容 a.b.txt 这类文件名
        int separatorIndex = fileName.lastIndexOf(".");
        if (separatorIndex < 0)
        {
            // 没有扩展名时返回空字符串，让白名单校验自然失败
            return "";
        }
        // 白名单比较不区分大小写，这里先统一转成小写
        return fileName.substring(separatorIndex + 1).toLowerCase();
    }

    /**
     * 根据图片文件头猜测扩展名
     * <p>
     * 当前覆盖 GIF、JPG/JFIF、BMP、PNG
     * 默认返回 JPG，主要用于图片字节写入类场景
     *
     * @param photoByte 图片字节，调用方需要保证至少包含文件头所需长度
     * @return 大写扩展名，例如 JPG、PNG
     */
    public static String getFileExtendName(byte[] photoByte)
    {
        // 默认按 JPG 处理，保持若依原有兜底行为
        String strFileExtendName = "JPG";
        // GIF 文件头通常以 GIF87a 或 GIF89a 开头
        if ((photoByte[0] == 71) && (photoByte[1] == 73) && (photoByte[2] == 70) && (photoByte[3] == 56)
                && ((photoByte[4] == 55) || (photoByte[4] == 57)) && (photoByte[5] == 97))
        {
            strFileExtendName = "GIF";
        }
        // JFIF 是常见 JPEG 文件头片段
        else if ((photoByte[6] == 74) && (photoByte[7] == 70) && (photoByte[8] == 73) && (photoByte[9] == 70))
        {
            strFileExtendName = "JPG";
        }
        // BMP 文件头以 BM 开头
        else if ((photoByte[0] == 66) && (photoByte[1] == 77))
        {
            strFileExtendName = "BMP";
        }
        // PNG 文件头中包含 PNG 标识
        else if ((photoByte[1] == 80) && (photoByte[2] == 78) && (photoByte[3] == 71))
        {
            strFileExtendName = "PNG";
        }
        return strFileExtendName;
    }
}
