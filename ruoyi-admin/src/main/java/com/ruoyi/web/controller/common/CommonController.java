package com.ruoyi.web.controller.common;

import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.x.file.storage.core.upload.MultipartUploadSupportInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.system.service.StorageFileApplicationService;

/**
 * 通用文件接口
 * <p>
 * 本地下载接口保留若依原有流程；存储文件上传、分片、预览和下载统一交给文件应用服务编排
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/common")
public class CommonController
{
    private static final Logger log = LoggerFactory.getLogger(CommonController.class);

    /**
     * 存储文件应用服务
     */
    private final StorageFileApplicationService storageFileApplicationService;

    /**
     * 构造通用文件控制器
     *
     * @param storageFileApplicationService 存储文件应用服务
     */
    public CommonController(StorageFileApplicationService storageFileApplicationService)
    {
        this.storageFileApplicationService = storageFileApplicationService;
    }

    /**
     * 从若依本地下载目录下载导出文件
     *
     * @param fileName 下载目录下的相对文件名
     * @param delete 下载完成后是否删除本地临时文件
     * @param response 响应对象
     * @param request 请求对象，保留若依原始签名兼容性
     */
    @GetMapping("/download")
    public void fileDownload(String fileName, Boolean delete, HttpServletResponse response, HttpServletRequest request)
    {
        try
        {
            // 下载前先校验文件名，避免路径穿越和非法扩展名
            if (!FileUtils.checkAllowDownload(fileName))
            {
                throw new Exception(StringUtils.format("文件名称({})非法，不允许下载", fileName));
            }
            // 导出文件名通常带时间戳前缀，这里生成浏览器看到的真实下载名
            String realFileName = System.currentTimeMillis() + fileName.substring(fileName.indexOf("_") + 1);
            // 拼接若依本地下载目录中的真实文件路径
            String filePath = RuoYiConfig.getDownloadPath() + fileName;

            // 设置附件响应头并把本地文件写入响应流
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, realFileName);
            FileUtils.writeBytes(filePath, response.getOutputStream());
            if (Boolean.TRUE.equals(delete))
            {
                // 临时导出文件按调用方要求在下载后删除
                FileUtils.deleteFile(filePath);
            }
        }
        catch (Exception e)
        {
            log.error("下载文件失败，fileName={}", fileName, e);
        }
    }

    /**
     * 通用单文件上传
     * <p>
     * platform 为空时使用配置中的默认平台；uploadScene 不为空时按场景配置覆盖大小、格式、缩略图和访问策略
     *
     * @param file 前端上传的文件
     * @param platform 可选存储平台标识，例如 local-1、aliyun-oss-1、minio-1
     * @param uploadScene 可选上传场景编码，例如 avatar、contract、importExcel
     * @param thumbnail 是否生成缩略图，仅图片文件支持
     * @param thumbnailWidth 缩略图宽度，为空时使用框架默认尺寸
     * @param thumbnailHeight 缩略图高度，为空时使用框架默认尺寸
     * @return 上传结果 DTO
     */
    @PostMapping("/upload")
    public AjaxResult uploadFile(MultipartFile file, @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "uploadScene", required = false) String uploadScene,
            @RequestParam(value = "thumbnail", defaultValue = "false") Boolean thumbnail,
            @RequestParam(value = "thumbnailWidth", required = false) Integer thumbnailWidth,
            @RequestParam(value = "thumbnailHeight", required = false) Integer thumbnailHeight) throws Exception
    {
        try
        {
            // 控制器只负责接收 HTTP 参数，具体上传编排交给文件应用服务
            return AjaxResult.success(storageFileApplicationService.uploadFile(file, platform, uploadScene, thumbnail,
                    thumbnailWidth, thumbnailHeight));
        }
        catch (Exception e)
        {
            // 上传失败记录上下文，方便排查对象存储配置、场景配置或文件限制问题
            log.error("上传文件失败，platform={}，uploadScene={}，originalFilename={}，size={}", platform, uploadScene,
                    file == null ? null : file.getOriginalFilename(), file == null ? null : file.getSize(), e);
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 通用多文件上传
     *
     * @param files 前端上传的文件集合
     * @param platform 可选存储平台标识，为空时使用默认平台
     * @param uploadScene 可选上传场景编码
     * @param thumbnail 是否生成缩略图，仅图片文件支持
     * @param thumbnailWidth 缩略图宽度
     * @param thumbnailHeight 缩略图高度
     * @return fileList 文件结果数组
     */
    @PostMapping("/uploads")
    public AjaxResult uploadFiles(List<MultipartFile> files, @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "uploadScene", required = false) String uploadScene,
            @RequestParam(value = "thumbnail", defaultValue = "false") Boolean thumbnail,
            @RequestParam(value = "thumbnailWidth", required = false) Integer thumbnailWidth,
            @RequestParam(value = "thumbnailHeight", required = false) Integer thumbnailHeight) throws Exception
    {
        try
        {
            // 批量上传的数量校验、逐个上传和失败补偿由应用服务统一处理
            AjaxResult ajax = AjaxResult.success();
            ajax.put("fileList", storageFileApplicationService.uploadFiles(files, platform, uploadScene, thumbnail,
                    thumbnailWidth, thumbnailHeight));
            return ajax;
        }
        catch (Exception e)
        {
            // 批量上传失败时记录公共上下文，具体失败文件由异常链辅助定位
            log.error("批量上传文件失败，platform={}，uploadScene={}，fileCount={}", platform, uploadScene,
                    files == null ? null : files.size(), e);
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 查询平台是否支持大文件分片上传
     *
     * @param platform 可选存储平台标识
     * @return 平台分片上传支持信息
     */
    @GetMapping("/multipart/support")
    public AjaxResult multipartSupport(@RequestParam(value = "platform", required = false) String platform)
    {
        // 支持能力由 X File Storage 根据平台适配器返回
        MultipartUploadSupportInfo supportInfo = storageFileApplicationService.multipartSupport(platform);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("platform", platform);
        ajax.put("support", supportInfo.getIsSupport());
        ajax.put("supportListParts", supportInfo.getIsSupportListParts());
        ajax.put("supportAbort", supportInfo.getIsSupportAbort());
        ajax.put("listPartsSupportMaxParts", supportInfo.getListPartsSupportMaxParts());
        return ajax;
    }

    /**
     * 初始化大文件分片上传
     *
     * @param originalFilename 原始文件名
     * @param size 文件总大小，单位字节
     * @param contentType 文件内容类型
     * @param platform 可选存储平台标识
     * @param uploadScene 可选上传场景编码
     * @return 分片上传会话信息
     */
    @PostMapping("/multipart/init")
    public AjaxResult initiateMultipartUpload(@RequestParam("originalFilename") String originalFilename,
            @RequestParam("size") Long size, @RequestParam(value = "contentType", required = false) String contentType,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "uploadScene", required = false) String uploadScene)
    {
        try
        {
            // 初始化阶段只创建上传会话和文件记录，不上传实际文件内容
            return AjaxResult.success(storageFileApplicationService.initiateMultipartUpload(originalFilename, size,
                    contentType, platform, uploadScene));
        }
        catch (Exception e)
        {
            // 初始化失败时记录声明大小和内容类型，便于排查场景限制或平台能力问题
            log.error("初始化分片上传失败，platform={}，uploadScene={}，originalFilename={}，size={}，contentType={}", platform,
                    uploadScene, originalFilename, size, contentType, e);
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 上传大文件分片
     *
     * @param fileId 初始化接口返回的文件ID，优先使用
     * @param url 初始化接口返回的文件真实地址，仅兼容旧前端
     * @param partNumber 分片编号，从 1 开始
     * @param file 当前分片文件
     * @return 当前分片上传结果
     */
    @PostMapping("/multipart/uploadPart")
    public AjaxResult uploadPart(@RequestParam(value = "fileId", required = false) Long fileId,
            @RequestParam(value = "url", required = false) String url, @RequestParam("partNumber") Integer partNumber,
            @RequestParam("file") MultipartFile file)
    {
        try
        {
            // 分片归属校验和真实上传动作统一交给应用服务
            return AjaxResult.success(storageFileApplicationService.uploadPart(fileId, url, partNumber, file));
        }
        catch (Exception e)
        {
            // 分片上传失败时记录 url 和分片编号，便于前端重试指定分片
            log.error("上传文件分片失败，url={}，partNumber={}，size={}", url, partNumber, file == null ? null : file.getSize(), e);
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 查询已经上传的大文件分片
     *
     * @param fileId 初始化接口返回的文件ID，优先使用
     * @param url 初始化接口返回的文件真实地址，仅兼容旧前端
     * @param maxParts 本次最多返回的分片数量
     * @param partNumberMarker 分页起始分片编号
     * @return 已上传分片列表
     */
    @GetMapping("/multipart/listParts")
    public AjaxResult listParts(@RequestParam(value = "fileId", required = false) Long fileId,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "maxParts", required = false) Integer maxParts,
            @RequestParam(value = "partNumberMarker", required = false) Integer partNumberMarker)
    {
        try
        {
            // 分片列表响应结构由应用服务统一维护
            return storageFileApplicationService.listParts(fileId, url, maxParts, partNumberMarker);
        }
        catch (Exception e)
        {
            // 查询分片失败时记录分页参数，便于判断是否平台不支持 listParts
            log.error("查询文件分片失败，url={}，maxParts={}，partNumberMarker={}", url, maxParts, partNumberMarker, e);
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 完成大文件分片上传
     *
     * @param fileId 初始化接口返回的文件ID，优先使用
     * @param url 初始化接口返回的文件真实地址，仅兼容旧前端
     * @return 合并后的文件信息
     */
    @PostMapping("/multipart/complete")
    public AjaxResult completeMultipartUpload(@RequestParam(value = "fileId", required = false) Long fileId,
            @RequestParam(value = "url", required = false) String url)
    {
        try
        {
            // 合并动作和返回 DTO 组装由应用服务统一处理
            return AjaxResult.success(storageFileApplicationService.completeMultipartUpload(fileId, url));
        }
        catch (Exception e)
        {
            // 合并失败时记录 url，便于根据 uploadId 和平台日志继续排查
            log.error("完成分片上传失败，url={}", url, e);
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 取消大文件分片上传
     *
     * @param fileId 初始化接口返回的文件ID，优先使用
     * @param url 初始化接口返回的文件真实地址，仅兼容旧前端
     * @return 取消后的文件信息
     */
    @PostMapping("/multipart/abort")
    public AjaxResult abortMultipartUpload(@RequestParam(value = "fileId", required = false) Long fileId,
            @RequestParam(value = "url", required = false) String url)
    {
        try
        {
            // 取消动作和返回 DTO 组装由应用服务统一处理
            return AjaxResult.success(storageFileApplicationService.abortMultipartUpload(fileId, url));
        }
        catch (Exception e)
        {
            // 取消失败时记录 url，便于后续人工清理未完成的分片会话
            log.error("取消分片上传失败，url={}", url, e);
            return AjaxResult.error(e.getMessage());
        }
    }

    /**
     * 下载 /profile 地址引用的本地资源
     *
     * @param resource /profile 资源路径
     * @param request 当前请求对象
     * @param response 响应对象
     */
    @GetMapping("/download/resource")
    public void resourceDownload(String resource, HttpServletRequest request, HttpServletResponse response)
            throws Exception
    {
        try
        {
            // 本地资源下载同样先做路径穿越和扩展名白名单校验
            if (!FileUtils.checkAllowDownload(resource))
            {
                throw new Exception(StringUtils.format("资源文件({})非法，不允许下载", resource));
            }
            // 去掉 /profile 前缀后拼接到 ruoyi.profile
            String downloadPath = RuoYiConfig.getProfile() + FileUtils.stripPrefix(resource);
            // 使用路径最后一段作为浏览器下载文件名
            String downloadName = StringUtils.substringAfterLast(downloadPath, "/");
            // 设置附件响应头并写出本地资源内容
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, downloadName);
            FileUtils.writeBytes(downloadPath, response.getOutputStream());
        }
        catch (Exception e)
        {
            log.error("下载资源文件失败，resource={}", resource, e);
        }
    }

    /**
     * 通过文件ID预览文件
     * <p>
     * 公开文件允许匿名访问，业务私有文件必须携带业务查询接口签发的短期 token
     *
     * @param fileId 文件ID
     * @param thumbnail true 表示预览缩略图
     * @param accessToken 文件短期访问 token
     * @param response 响应对象
     */
    @GetMapping("/file/preview/{fileId}")
    public void previewStorageFile(@PathVariable Long fileId,
            @RequestParam(value = "thumbnail", defaultValue = "false") Boolean thumbnail,
            @RequestParam(value = "token", required = false) String accessToken, HttpServletResponse response)
    {
        // 公共预览入口只负责转交文件流输出，访问校验由应用服务统一处理
        storageFileApplicationService.writeStorageFile(fileId, Boolean.TRUE.equals(thumbnail), false, accessToken,
                response);
    }

    /**
     * 通过文件ID下载文件
     * <p>
     * 公开文件允许匿名下载，业务私有文件必须携带业务查询接口签发的短期 token
     *
     * @param fileId 文件ID
     * @param accessToken 文件短期访问 token
     * @param response 响应对象
     */
    @RequestMapping(value = "/file/download/{fileId}", method = { RequestMethod.GET, RequestMethod.POST })
    @Log(title = "存储文件下载", businessType = BusinessType.EXPORT)
    public void downloadStorageFile(@PathVariable Long fileId,
            @RequestParam(value = "token", required = false) String accessToken, HttpServletResponse response)
    {
        // 公共下载入口只负责转交文件流输出，访问校验由应用服务统一处理
        storageFileApplicationService.writeStorageFile(fileId, false, true, accessToken, response);
    }
}
