package com.ruoyi.common.utils.file;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import org.apache.poi.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.utils.StringUtils;

/**
 * 图片读取工具类
 * <p>
 * 同时支持远程 HTTP 图片地址和若依历史本地 /profile 资源路径
 * 导出、导入等需要读取图片字节的功能，可以不关心图片当前存储在本地还是对象存储
 *
 * @author ruoyi
 */
public class ImageUtils
{
    private static final Logger log = LoggerFactory.getLogger(ImageUtils.class);

    /**
     * 将图片读取为字节数组
     *
     * @param imagePath HTTP 图片 URL 或 /profile 资源路径
     * @return 图片字节；读取失败时返回 null
     */
    public static byte[] getImage(String imagePath)
    {
        // 先把图片路径统一转换成输入流
        InputStream is = getFile(imagePath);
        try
        {
            // 调用方通常需要字节数组，例如导出 Excel 图片
            return IOUtils.toByteArray(is);
        }
        catch (Exception e)
        {
            log.error("图片加载异常 {}", e);
            return null;
        }
        finally
        {
            // 读取完成后安静关闭输入流
            IOUtils.closeQuietly(is);
        }
    }

    /**
     * 将图片路径读取为输入流
     * <p>
     * 这里先读成字节数组再包装成 ByteArrayInputStream，便于调用方重复按流式 API 使用
     *
     * @param imagePath HTTP 图片 URL 或 /profile 资源路径
     * @return 图片输入流；读取失败时返回 null
     */
    public static InputStream getFile(String imagePath)
    {
        try
        {
            // 统一读取远程或本地图片字节
            byte[] result = readFile(imagePath);
            // 复制一份字节，避免外部引用影响后续输入流内容
            result = Arrays.copyOf(result, result.length);
            // 包装成内存输入流交给调用方
            return new ByteArrayInputStream(result);
        }
        catch (Exception e)
        {
            log.error("获取图片异常 {}", e);
        }
        return null;
    }

    /**
     * 读取远程或本地图片文件为字节数组
     * <p>
     * http 开头的路径会直接发起网络读取，并设置连接和读取超时
     * 非 http 路径会按若依 /profile 资源处理，从 ruoyi.profile 下读取本地文件
     *
     * @param url HTTP 图片 URL 或 /profile 资源路径
     * @return 文件字节；读取失败时返回 null
     */
    public static byte[] readFile(String url)
    {
        InputStream in = null;
        try
        {
            // 远程 URL 直接按 HTTP 资源读取
            if (url.startsWith("http"))
            {
                // 对象存储公开 URL 或其它公网图片地址，通过 HTTP 读取
                URL urlObj = new URL(url);
                URLConnection urlConnection = urlObj.openConnection();
                // 连接超时控制，避免导出时长时间卡住
                urlConnection.setConnectTimeout(30 * 1000);
                // 读取超时控制，避免大图或网络异常阻塞线程
                urlConnection.setReadTimeout(60 * 1000);
                urlConnection.setDoInput(true);
                in = urlConnection.getInputStream();
            }
            else
            {
                // 历史本地资源去掉 /profile 前缀后，从 ruoyi.profile 下解析真实文件
                String localPath = RuoYiConfig.getProfile();
                String downloadPath = localPath + StringUtils.substringAfter(url, Constants.RESOURCE_PREFIX);
                in = new FileInputStream(downloadPath);
            }
            // 将输入流完整转换成字节数组
            return IOUtils.toByteArray(in);
        }
        catch (Exception e)
        {
            log.error("获取文件路径异常 {}", e);
            return null;
        }
        finally
        {
            // 无论远程还是本地读取，都需要关闭输入流
            IOUtils.closeQuietly(in);
        }
    }
}
