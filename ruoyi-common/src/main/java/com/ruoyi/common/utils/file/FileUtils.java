package com.ruoyi.common.utils.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;

/**
 * 文件处理工具类
 * <p>
 * 本类主要面向本地文件和若依 /profile 资源路径。对象存储上传会通过
 * X File Storage 返回远程 URL；历史本地资源、导入导出临时文件仍继续使用本工具
 *
 * @author ruoyi
 */
public class FileUtils
{
    /**
     * 简单文件名允许的字符范围
     * <p>
     * 该规则故意保持较窄范围，不包含路径分隔符，用于降低路径注入风险
     */
    public static String FILENAME_PATTERN = "[a-zA-Z0-9_\\-\\|\\.\\u4e00-\\u9fa5]+";

    /**
     * 将本地文件写入输出流
     * <p>
     * 该方法会在 finally 中关闭输出流，保持若依原有下载接口行为
     *
     * @param filePath 本地绝对文件路径
     * @param os 目标输出流
     * @throws IOException 文件不存在或写出失败时抛出
     */
    public static void writeBytes(String filePath, OutputStream os) throws IOException
    {
        FileInputStream fis = null;
        try
        {
            // 根据绝对路径定位本地文件
            File file = new File(filePath);
            if (!file.exists())
            {
                // 文件不存在时直接抛出异常，由调用方统一记录下载失败
                throw new FileNotFoundException(filePath);
            }
            // 使用文件输入流分块读取，避免一次性加载大文件
            fis = new FileInputStream(file);
            byte[] b = new byte[1024];
            int length;
            while ((length = fis.read(b)) > 0)
            {
                // 每次只写入本轮实际读取的字节数
                os.write(b, 0, length);
            }
        }
        catch (IOException e)
        {
            throw e;
        }
        finally
        {
            // 若依原逻辑会关闭响应输出流和文件输入流
            IOUtils.close(os);
            IOUtils.close(fis);
        }
    }

    /**
     * 将字节内容写入配置的导入目录
     *
     * @param data 文件字节
     * @return 写入后的 /profile 资源路径
     * @throws IOException 写入失败时抛出
     */
    public static String writeImportBytes(byte[] data) throws IOException
    {
        // 导入场景统一写入 ruoyi.profile/import 目录
        return writeBytes(data, RuoYiConfig.getImportPath());
    }

    /**
     * 将字节内容写入指定本地上传目录
     * <p>
     * 文件名使用日期目录 + UUID + 根据文件头推断的扩展名
     *
     * @param data 文件字节
     * @param uploadDir 本地绝对上传目录
     * @return 写入后的 /profile 资源路径
     * @throws IOException 写入失败时抛出
     */
    public static String writeBytes(byte[] data, String uploadDir) throws IOException
    {
        FileOutputStream fos = null;
        String pathName = "";
        try
        {
            // 根据文件头推断扩展名，常用于图片字节写入
            String extension = getFileExtendName(data);
            // 日期目录加 UUID 文件名，降低目录过大和文件重名风险
            pathName = DateUtils.datePath() + "/" + IdUtils.fastUUID() + "." + extension;
            // 获取真实文件对象并创建父目录
            File file = FileUploadUtils.getAbsoluteFile(uploadDir, pathName);
            fos = new FileOutputStream(file);
            // 将字节数组完整写入本地文件
            fos.write(data);
        }
        finally
        {
            // 写入完成后关闭文件输出流
            IOUtils.close(fos);
        }
        // 返回若依 /profile 资源路径
        return FileUploadUtils.getPathFileName(uploadDir, pathName);
    }

    /**
     * 移除资源路径中的 /profile 前缀
     *
     * @param filePath 资源路径，例如 /profile/avatar/a.png
     * @return 去掉 /profile 后的路径
     */
    public static String stripPrefix(String filePath)
    {
        // substringAfter 会返回 /profile 之后的相对路径
        return StringUtils.substringAfter(filePath, Constants.RESOURCE_PREFIX);
    }

    /**
     * 删除本地文件
     *
     * @param filePath 本地绝对文件路径
     * @return true 表示删除成功
     */
    public static boolean deleteFile(String filePath)
    {
        boolean flag = false;
        File file = new File(filePath);
        // 只删除普通文件，目录或不存在的路径直接忽略
        if (file.isFile() && file.exists())
        {
            // 删除结果由 File.delete 返回，调用方可据此判断是否成功
            flag = file.delete();
        }
        return flag;
    }

    /**
     * 校验简单文件名是否合法
     *
     * @param filename 不包含路径分隔符的文件名
     * @return true 表示文件名合法
     */
    public static boolean isValidFilename(String filename)
    {
        // 仅允许预设字符范围，不允许路径分隔符
        return filename.matches(FILENAME_PATTERN);
    }

    /**
     * 检查请求的资源是否允许下载
     * <p>
     * 这里会阻止路径穿越，并且只允许 {@link MimeTypeUtils#DEFAULT_ALLOWED_EXTENSION}
     * 白名单中的扩展名
     *
     * @param resource 客户端请求的资源路径或文件名
     * @return true 表示通过基础安全检查
     */
    public static boolean checkAllowDownload(String resource)
    {
        // 阻止 ../secret.txt 这类目录穿越路径
        if (StringUtils.contains(resource, ".."))
        {
            return false;
        }

        // 下载扩展名复用通用上传白名单
        if (ArrayUtils.contains(MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION, FileTypeUtils.getFileType(resource)))
        {
            return true;
        }

        // 未命中白名单的文件不允许下载
        return false;
    }

    /**
     * 按浏览器类型编码下载文件名
     *
     * @param request 当前请求对象
     * @param fileName 原始文件名
     * @return 编码后的文件名
     * @throws UnsupportedEncodingException 编码不支持时抛出
     */
    public static String setFileDownloadHeader(HttpServletRequest request, String fileName) throws UnsupportedEncodingException
    {
        // 读取 User-Agent，用于兼容不同浏览器的文件名编码差异
        final String agent = request.getHeader("USER-AGENT");
        String filename = fileName;
        if (agent.contains("MSIE"))
        {
            // IE 使用 URL 编码并把加号还原为空格
            filename = URLEncoder.encode(filename, "utf-8");
            filename = filename.replace("+", " ");
        }
        else if (agent.contains("Firefox"))
        {
            // Firefox 走 ISO8859-1 字节转换逻辑
            filename = new String(fileName.getBytes(), "ISO8859-1");
        }
        else if (agent.contains("Chrome"))
        {
            // Chrome 使用 UTF-8 URL 编码
            filename = URLEncoder.encode(filename, "utf-8");
        }
        else
        {
            // 其它浏览器按通用 UTF-8 URL 编码处理
            filename = URLEncoder.encode(filename, "utf-8");
        }
        return filename;
    }

    /**
     * 设置附件下载响应头
     * <p>
     * 同时暴露 Content-Disposition 和 download-filename，方便前端跨域请求后读取文件名
     *
     * @param response 响应对象
     * @param realFileName 展示给客户端的下载文件名
     * @throws UnsupportedEncodingException 编码不支持时抛出
     */
    public static void setAttachmentResponseHeader(HttpServletResponse response, String realFileName) throws UnsupportedEncodingException
    {
        // 先对中文文件名做百分号编码
        String percentEncodedFileName = percentEncode(realFileName);

        // Content-Disposition 同时设置 filename 和 RFC 5987 filename*
        StringBuilder contentDispositionValue = new StringBuilder();
        contentDispositionValue.append("attachment; filename=")
                .append(percentEncodedFileName)
                .append(";")
                .append("filename*=")
                .append("utf-8''")
                .append(percentEncodedFileName);

        // 暴露响应头，便于前端在跨域下载时读取文件名
        response.addHeader("Access-Control-Expose-Headers", "Content-Disposition,download-filename");
        response.setHeader("Content-disposition", contentDispositionValue.toString());
        response.setHeader("download-filename", percentEncodedFileName);
    }

    /**
     * 对字符串做 HTTP 头可用的百分号编码
     *
     * @param s 原始字符串
     * @return 编码后的字符串，空格会编码为 %20
     * @throws UnsupportedEncodingException 编码不支持时抛出
     */
    public static String percentEncode(String s) throws UnsupportedEncodingException
    {
        // URLEncoder 会把空格编码成加号
        String encode = URLEncoder.encode(s, StandardCharsets.UTF_8.toString());
        // 下载响应头中使用 %20 表示空格更稳妥
        return encode.replaceAll("\\+", "%20");
    }

    /**
     * 根据图片文件头猜测扩展名
     *
     * @param photoByte 图片字节，调用方需要保证至少包含文件头所需长度
     * @return 小写扩展名，默认 jpg
     */
    public static String getFileExtendName(byte[] photoByte)
    {
        // 默认按 jpg 处理，保持若依原有兜底行为
        String strFileExtendName = "jpg";
        // GIF 文件头通常以 GIF87a 或 GIF89a 开头
        if ((photoByte[0] == 71) && (photoByte[1] == 73) && (photoByte[2] == 70) && (photoByte[3] == 56)
                && ((photoByte[4] == 55) || (photoByte[4] == 57)) && (photoByte[5] == 97))
        {
            strFileExtendName = "gif";
        }
        // JFIF 是常见 JPEG 文件头片段
        else if ((photoByte[6] == 74) && (photoByte[7] == 70) && (photoByte[8] == 73) && (photoByte[9] == 70))
        {
            strFileExtendName = "jpg";
        }
        // BMP 文件头以 BM 开头
        else if ((photoByte[0] == 66) && (photoByte[1] == 77))
        {
            strFileExtendName = "bmp";
        }
        // PNG 文件头中包含 PNG 标识
        else if ((photoByte[1] == 80) && (photoByte[2] == 78) && (photoByte[3] == 71))
        {
            strFileExtendName = "png";
        }
        return strFileExtendName;
    }

    /**
     * 从路径中提取文件名
     * <p>
     * 例如 /profile/upload/2022/04/16/ruoyi.png 返回 ruoyi.png
     *
     * @param fileName 路径或文件名
     * @return 不包含父路径的文件名；入参为 null 时返回 null
     */
    public static String getName(String fileName)
    {
        if (fileName == null)
        {
            // 入参为空时保持空返回，避免调用方额外判空
            return null;
        }
        // 同时兼容 URL 路径和 Windows 本地路径
        int lastUnixPos = fileName.lastIndexOf('/');
        int lastWindowsPos = fileName.lastIndexOf('\\');
        int index = Math.max(lastUnixPos, lastWindowsPos);
        // 取最后一个路径分隔符后面的文件名
        return fileName.substring(index + 1);
    }

    /**
     * 从路径中提取不带扩展名的文件名
     * <p>
     * 例如 /profile/upload/2022/04/16/ruoyi.png 返回 ruoyi
     *
     * @param fileName 路径或文件名
     * @return 不包含父路径和扩展名的文件名；入参为 null 时返回 null
     */
    public static String getNameNotSuffix(String fileName)
    {
        if (fileName == null)
        {
            // 入参为空时保持空返回，避免调用方额外判空
            return null;
        }
        // FilenameUtils 会处理路径分隔符和扩展名截断
        String baseName = FilenameUtils.getBaseName(fileName);
        return baseName;
    }
}
