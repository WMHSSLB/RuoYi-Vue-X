package com.ruoyi.web.controller.common;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.dromara.x.file.storage.core.upload.FilePartInfoList;
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
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.constant.StorageFileConstants;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.StorageFilePartRespDTO;
import com.ruoyi.common.core.domain.model.StorageFileUploadRespDTO;
import com.ruoyi.common.enums.StorageFileAccess;
import com.ruoyi.common.enums.StorageFileStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
import com.ruoyi.common.utils.file.XFileStorageUtils;
import com.ruoyi.system.domain.SysStorageFile;
import com.ruoyi.system.service.ISysStorageFileService;
import com.ruoyi.system.service.StorageFileAccessTokenService;
import com.ruoyi.system.service.StorageFileAccessUrlBuilder;

/**
 * 通用文件接口
 * <p>
 * 下载接口保留若依原有本地文件流程；上传接口统一交给 XFileStorageUtils 处理，支持默认平台和显式 platform 平台
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/common")
public class CommonController
{
    private static final Logger log = LoggerFactory.getLogger(CommonController.class);

    /**
     * X File Storage 上传适配工具
     */
    private final XFileStorageUtils xFileStorageUtils;

    /**
     * 存储文件记录服务，用于私有文件下载鉴权和文件资产记录查询
     */
    private final ISysStorageFileService storageFileService;

    /**
     * 文件对外访问地址构建器
     */
    private final StorageFileAccessUrlBuilder storageFileAccessUrlBuilder;

    /**
     * 存储文件短期访问授权服务
     */
    private final StorageFileAccessTokenService storageFileAccessTokenService;

    /**
     * 构造通用文件控制器
     *
     * @param xFileStorageUtils 统一上传工具
     * @param storageFileService 存储文件记录服务
     * @param storageFileAccessUrlBuilder 文件对外访问地址构建器
     * @param storageFileAccessTokenService 存储文件短期访问授权服务
     */
    public CommonController(XFileStorageUtils xFileStorageUtils, ISysStorageFileService storageFileService,
            StorageFileAccessUrlBuilder storageFileAccessUrlBuilder, StorageFileAccessTokenService storageFileAccessTokenService)
    {
        this.xFileStorageUtils = xFileStorageUtils;
        this.storageFileService = storageFileService;
        this.storageFileAccessUrlBuilder = storageFileAccessUrlBuilder;
        this.storageFileAccessTokenService = storageFileAccessTokenService;
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
            // 统一使用工具类处理平台选择、场景规则、格式校验、缩略图和真实上传
            FileInfo fileInfo = xFileStorageUtils.upload(file, platform, uploadScene, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION,
                    Boolean.TRUE.equals(thumbnail), thumbnailWidth, thumbnailHeight);
            // 单文件上传返回 data 对象，字段由 DTO 统一维护
            return AjaxResult.success(buildUploadRespDTO(fileInfo, uploadScene, file.getOriginalFilename()));
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
        List<String> uploadedUrls = new ArrayList<String>();
        try
        {
            // 批量数量优先读取上传场景配置，场景未配置时回退到全局配置
            xFileStorageUtils.assertBatchUploadAllowed(files, uploadScene);
            // 结构化文件列表给新前端使用，每个元素对应一个上传文件
            List<StorageFileUploadRespDTO> fileList = new ArrayList<StorageFileUploadRespDTO>();
            // 已成功上传的 URL 会被收集起来，后续任一文件失败时统一做补偿删除
            for (MultipartFile file : files)
            {
                // 每个文件使用同一套 platform 和 uploadScene 策略上传
                FileInfo fileInfo = xFileStorageUtils.upload(file, platform, uploadScene, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION,
                        Boolean.TRUE.equals(thumbnail), thumbnailWidth, thumbnailHeight);
                uploadedUrls.add(fileInfo.getUrl());
                // DTO 内部统一处理 url、缩略图、大小、access 等返回字段
                fileList.add(buildUploadRespDTO(fileInfo, uploadScene, file.getOriginalFilename()));
            }
            // 批量上传继续保留 fileList 字段，前端按数组逐项处理
            AjaxResult ajax = AjaxResult.success();
            ajax.put("fileList", fileList);
            return ajax;
        }
        catch (Exception e)
        {
            compensateUploadedFiles(uploadedUrls);
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
        MultipartUploadSupportInfo supportInfo = xFileStorageUtils.isSupportMultipartUpload(platform);
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
            FileInfo fileInfo = xFileStorageUtils.initiateMultipartUpload(originalFilename, size, contentType, platform, uploadScene,
                    MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
            // 分片初始化返回统一 DTO，前端后续用 url 和 uploadId 继续传分片
            return AjaxResult.success(buildUploadRespDTO(fileInfo, uploadScene, originalFilename));
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
     * @param url 初始化接口返回的文件地址
     * @param partNumber 分片编号，从 1 开始
     * @param file 当前分片文件
     * @return 当前分片上传结果
     */
    @PostMapping("/multipart/uploadPart")
    public AjaxResult uploadPart(@RequestParam("url") String url, @RequestParam("partNumber") Integer partNumber,
            @RequestParam("file") MultipartFile file)
    {
        try
        {
            // 分片上传成功后框架会回调 FileRecorder.saveFilePart 保存分片记录
            storageFileService.checkMultipartOwner(url);
            FilePartInfo filePartInfo = xFileStorageUtils.uploadPart(url, partNumber, file);
            return AjaxResult.success(StorageFilePartRespDTO.from(filePartInfo));
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
     * @param url 初始化接口返回的文件地址
     * @param maxParts 本次最多返回的分片数量
     * @param partNumberMarker 分页起始分片编号
     * @return 已上传分片列表
     */
    @GetMapping("/multipart/listParts")
    public AjaxResult listParts(@RequestParam("url") String url,
            @RequestParam(value = "maxParts", required = false) Integer maxParts,
            @RequestParam(value = "partNumberMarker", required = false) Integer partNumberMarker)
    {
        try
        {
            // 部分平台不支持列举分片，具体能力可先调用 support 接口确认
            storageFileService.checkMultipartOwner(url);
            FilePartInfoList filePartInfoList = xFileStorageUtils.listParts(url, maxParts, partNumberMarker);
            AjaxResult ajax = AjaxResult.success();
            ajax.put("file", buildUploadRespDTO(filePartInfoList.getFileInfo(), null, null));
            ajax.put("partList", toFilePartList(filePartInfoList.getList()));
            ajax.put("maxParts", filePartInfoList.getMaxParts());
            ajax.put("truncated", filePartInfoList.getIsTruncated());
            ajax.put("partNumberMarker", filePartInfoList.getPartNumberMarker());
            ajax.put("nextPartNumberMarker", filePartInfoList.getNextPartNumberMarker());
            return ajax;
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
     * @param url 初始化接口返回的文件地址
     * @return 合并后的文件信息
     */
    @PostMapping("/multipart/complete")
    public AjaxResult completeMultipartUpload(@RequestParam("url") String url)
    {
        try
        {
            // 合并成功后框架会更新主文件记录并删除分片记录
            storageFileService.checkMultipartOwner(url);
            FileInfo fileInfo = xFileStorageUtils.completeMultipartUpload(url);
            return AjaxResult.success(buildUploadRespDTO(fileInfo, null, null));
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
     * @param url 初始化接口返回的文件地址
     * @return 取消后的文件信息
     */
    @PostMapping("/multipart/abort")
    public AjaxResult abortMultipartUpload(@RequestParam("url") String url)
    {
        try
        {
            // 取消成功后框架会通知平台释放未合并分片
            storageFileService.checkMultipartOwner(url);
            FileInfo fileInfo = xFileStorageUtils.abortMultipartUpload(url);
            return AjaxResult.success(buildUploadRespDTO(fileInfo, null, null));
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
     * 公开文件允许匿名访问，私有文件必须携带登录令牌并通过通用下载规则
     *
     * @param fileId 文件ID
     * @param thumbnail true 表示预览缩略图
     * @param response 响应对象
     */
    @GetMapping("/file/preview/{fileId}")
    public void previewStorageFile(@PathVariable Long fileId,
            @RequestParam(value = "thumbnail", defaultValue = "false") Boolean thumbnail,
            @RequestParam(value = "token", required = false) String accessToken, HttpServletResponse response)
    {
        // 公共预览入口统一通过 fileId 读取文件，私有业务文件必须带业务接口签发的短期 token
        writeStorageFile(fileId, Boolean.TRUE.equals(thumbnail), false, accessToken, response);
    }

    /**
     * 通过文件ID下载文件
     * <p>
     * 公开文件允许匿名下载，私有文件必须携带登录令牌并通过通用下载规则
     *
     * @param fileId 文件ID
     * @param response 响应对象
     */
    @RequestMapping(value = "/file/download/{fileId}", method = { RequestMethod.GET, RequestMethod.POST })
    public void downloadStorageFile(@PathVariable Long fileId,
            @RequestParam(value = "token", required = false) String accessToken, HttpServletResponse response)
    {
        // 公共下载入口统一输出文件流，私有业务文件必须带业务接口签发的短期 token
        writeStorageFile(fileId, false, true, accessToken, response);
    }

    /**
     * 将分片列表转换为接口返回 DTO 列表
     *
     * @param filePartInfos X File Storage 分片列表
     * @return 分片返回 DTO 列表
     */
    private List<StorageFilePartRespDTO> toFilePartList(List<FilePartInfo> filePartInfos)
    {
        // 空分片列表返回空数组，前端无需做 null 判断
        List<StorageFilePartRespDTO> partList = new ArrayList<StorageFilePartRespDTO>();
        if (filePartInfos == null)
        {
            return partList;
        }
        for (FilePartInfo filePartInfo : filePartInfos)
        {
            // 每个分片转换为稳定 DTO 后返回
            partList.add(StorageFilePartRespDTO.from(filePartInfo));
        }
        return partList;
    }

    /**
     * 校验当前登录用户是否允许下载存储文件
     *
     * @param storageFile 存储文件记录
     * @param bizType 请求声明的业务类型
     * @param bizId 请求声明的业务ID
     */
    private void checkStorageDownloadAllowed(SysStorageFile storageFile, String bizType, String bizId, String accessToken)
    {
        if (storageFile == null)
        {
            throw new ServiceException("文件记录不存在");
        }
        // 待删除和已删除文件不允许继续通过历史授权访问
        if (StorageFileStatus.isInvalid(storageFile.getFileStatus()))
        {
            throw new ServiceException("文件已失效");
        }
        // 公开文件本身可以走静态资源访问，登录下载入口也允许读取
        if (isPublicStorageFile(storageFile))
        {
            return;
        }
        // 私有文件只要携带了有效的短期访问 token，就说明业务接口已经完成业务权限校验
        if (storageFileAccessTokenService.verifyToken(storageFile.getFileId(), accessToken))
        {
            return;
        }
        // 已绑定业务的普通私有文件不能继续按登录用户兜底，否则会绕开业务模块自己的菜单权限和数据权限
        if (StringUtils.isNotBlank(storageFile.getBizType())
                && !StorageFileConstants.BIZ_TYPE_AVATAR.equals(storageFile.getBizType()))
        {
            throw new ServiceException("业务私有文件缺少访问授权");
        }
        // 超级管理员和拥有统一下载权限的用户只兜底处理非业务绑定文件，业务私有文件请走业务接口签发 token
        if (hasStorageDownloadPermission())
        {
            return;
        }
        String username = SecurityUtils.getUsername();
        // 临时文件或未绑定业务文件只允许上传人自己下载，已绑定业务后要继续走业务身份校验
        if (StringUtils.isBlank(storageFile.getBizType()) && StringUtils.isNotBlank(storageFile.getCreateBy())
                && storageFile.getCreateBy().equals(username))
        {
            return;
        }
        // 头像属于用户自身资料，当前用户只能下载自己的头像文件
        if (StorageFileConstants.BIZ_TYPE_AVATAR.equals(storageFile.getBizType())
                && String.valueOf(SecurityUtils.getUserId()).equals(storageFile.getBizId()))
        {
            return;
        }
        // 走到这里说明既不是公开文件，也不是本人临时文件、本人头像或已绑定业务文件
        throw new ServiceException("没有权限访问文件");
    }

    /**
     * 判断文件是否是公开访问文件
     *
     * @param storageFile 存储文件记录
     * @return true 表示公开文件
     */
    private boolean isPublicStorageFile(SysStorageFile storageFile)
    {
        // 访问级别以文件记录表 access 字段为准，不再在下载鉴权时解析存储路径
        return StorageFileAccess.isPublic(storageFile.getAccess());
    }

    /**
     * 判断文件是否是私有访问文件
     *
     * @param storageFile 存储文件记录
     * @return true 表示私有文件
     */
    private boolean isPrivateStorageFile(SysStorageFile storageFile)
    {
        // 私有文件必须通过后端代理和短期 token 访问，不能把真实对象存储地址暴露给前端
        return storageFile != null && StorageFileAccess.isPrivate(storageFile.getAccess());
    }

    /**
     * 判断当前用户是否拥有存储文件管理下载权限
     *
     * @return true 表示拥有管理下载权限
     */
    private boolean hasStorageDownloadPermission()
    {
        try
        {
            // 文件预览下载入口允许匿名进入，未登录时 SecurityUtils 会抛出未授权异常，这里按无权限处理
            return SecurityUtils.isAdmin() || SecurityUtils.hasPermi(StorageFileConstants.PERMISSION_STORAGE_DOWNLOAD);
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * 构造上传返回 DTO
     *
     * @param fileInfo X File Storage 文件信息
     * @param uploadScene 上传场景编码
     * @param originalFilename 原始文件名兜底值
     * @return 上传结果 DTO
     */
    private StorageFileUploadRespDTO buildUploadRespDTO(FileInfo fileInfo, String uploadScene, String originalFilename)
    {
        StorageFileUploadRespDTO resp = StorageFileUploadRespDTO.from(fileInfo, uploadScene, originalFilename);
        if (fileInfo == null || StringUtils.isBlank(fileInfo.getUrl()))
        {
            return resp;
        }
        // 上传成功后 FileRecorder 已经写入文件表，这里按 URL 反查 fileId、access 和对象 Key
        SysStorageFile storageFile = storageFileService.selectStorageFileByUrl(fileInfo.getUrl());
        if (storageFile != null)
        {
            resp.setFileId(storageFile.getFileId());
            resp.setAccess(storageFile.getAccess());
            resp.setObjectKey(storageFile.getObjectKey());
            resp.setThObjectKey(storageFile.getThObjectKey());
            // previewUrl 是前端展示用地址，私有文件会被转换为后端代理地址
            resp.setPreviewUrl(storageFileAccessUrlBuilder.buildPreviewUrl(storageFile));
            // downloadUrl 是前端下载用地址，后续下载日志和权限控制都可以集中在代理入口
            resp.setDownloadUrl(storageFileAccessUrlBuilder.buildDownloadUrl(storageFile));
            // thPreviewUrl 是缩略图展示地址，未生成缩略图时返回 null
            resp.setThPreviewUrl(storageFileAccessUrlBuilder.buildThumbnailPreviewUrl(storageFile));
            if (isPrivateStorageFile(storageFile))
            {
                // 上传接口返回的是临时文件，私有临时文件也需要短期 token 才能在保存业务前预览
                String accessToken = storageFileAccessTokenService.issueUserBoundToken(storageFile.getFileId());
                resp.setPreviewUrl(storageFileAccessTokenService.appendToken(resp.getPreviewUrl(), accessToken));
                resp.setDownloadUrl(storageFileAccessTokenService.appendToken(resp.getDownloadUrl(), accessToken));
                resp.setThPreviewUrl(storageFileAccessTokenService.appendToken(resp.getThPreviewUrl(), accessToken));
            }
            // 私有文件不向前端暴露真实存储 URL，兼容字段也统一返回代理预览地址
            if (!storageFileAccessUrlBuilder.allowExposeRealUrl(storageFile))
            {
                resp.setUrl(resp.getPreviewUrl());
                resp.setFileName(resp.getPreviewUrl());
                resp.setThUrl(resp.getThPreviewUrl());
                resp.setThFileName(resp.getThPreviewUrl());
            }
            else
            {
                // 公开直连模式下 previewUrl 与真实 URL 一致，保持若依原有前端使用习惯
                resp.setUrl(resp.getPreviewUrl());
                resp.setFileName(resp.getPreviewUrl());
                resp.setThUrl(resp.getThPreviewUrl());
                resp.setThFileName(resp.getThPreviewUrl());
            }
        }
        return resp;
    }

    /**
     * 写出文件内容
     *
     * @param fileId 文件ID
     * @param thumbnail true 表示写出缩略图
     * @param attachment true 表示作为附件下载
     * @param response 响应对象
     */
    private void writeStorageFile(Long fileId, boolean thumbnail, boolean attachment, String accessToken,
            HttpServletResponse response)
    {
        SysStorageFile storageFile = storageFileService.selectStorageFileById(fileId);
        try
        {
            checkStorageDownloadAllowed(storageFile, null, null, accessToken);
            FileInfo fileInfo = buildDownloadFileInfo(storageFile, thumbnail);
            if (fileInfo == null)
            {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            String downloadName = StringUtils.isNotBlank(fileInfo.getOriginalFilename()) ? fileInfo.getOriginalFilename()
                    : FileUtils.getName(fileInfo.getUrl());
            response.setContentType(StringUtils.isNotBlank(fileInfo.getContentType()) ? fileInfo.getContentType()
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE);
            if (attachment)
            {
                // 下载入口设置附件响应头，预览入口则让浏览器按 Content-Type 尝试打开
                FileUtils.setAttachmentResponseHeader(response, downloadName);
            }
            response.getOutputStream().write(xFileStorageUtils.downloadBytes(fileInfo));
        }
        catch (ServiceException e)
        {
            log.warn("访问存储文件被拒绝，fileId={}，thumbnail={}，attachment={}，reason={}", fileId, thumbnail, attachment,
                    e.getMessage());
            // ServiceException 可能只传业务提示不传错误码，默认按 403 访问拒绝处理
            Integer code = e.getCode();
            response.setStatus(code != null && code == HttpStatus.UNAUTHORIZED ? HttpServletResponse.SC_UNAUTHORIZED
                    : HttpServletResponse.SC_FORBIDDEN);
        }
        catch (Exception e)
        {
            log.error("访问存储文件失败，fileId={}，thumbnail={}，attachment={}", fileId, thumbnail, attachment, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 构建下载用文件信息
     *
     * @param storageFile 文件记录
     * @param thumbnail true 表示下载缩略图
     * @return X File Storage 文件信息
     */
    private FileInfo buildDownloadFileInfo(SysStorageFile storageFile, boolean thumbnail)
    {
        FileInfo fileInfo = storageFileService.toFileInfo(storageFile);
        if (fileInfo == null || !thumbnail || StringUtils.isBlank(storageFile.getThUrl()))
        {
            return fileInfo;
        }
        // 缩略图和原图在同一平台同一路径下，只切换 URL、文件名、大小和内容类型
        fileInfo.setUrl(storageFile.getThUrl());
        fileInfo.setFilename(storageFile.getThFilename());
        fileInfo.setSize(storageFile.getThSize());
        fileInfo.setContentType(storageFile.getThContentType());
        return fileInfo;
    }

    /**
     * 补偿删除批量上传中已经成功的文件
     *
     * @param uploadedUrls 已经上传成功的文件 URL
     */
    private void compensateUploadedFiles(List<String> uploadedUrls)
    {
        if (uploadedUrls == null || uploadedUrls.isEmpty())
        {
            return;
        }
        for (String uploadedUrl : uploadedUrls)
        {
            try
            {
                // 批量上传只要中途失败，就立即删除此前已经上传成功的文件，避免残留孤儿文件
                xFileStorageUtils.delete(uploadedUrl);
            }
            catch (Exception ex)
            {
                log.warn("批量上传失败后补偿删除文件失败，url={}", uploadedUrl, ex);
            }
        }
    }

}
