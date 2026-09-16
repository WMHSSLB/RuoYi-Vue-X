package com.ruoyi.system.service;

import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.dromara.x.file.storage.core.upload.FilePartInfoList;
import org.dromara.x.file.storage.core.upload.MultipartUploadSupportInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.model.StorageFilePartRespDTO;
import com.ruoyi.common.core.domain.model.StorageFileUploadRespDTO;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
import com.ruoyi.common.utils.file.XFileStorageUtils;
import com.ruoyi.system.domain.SysStorageFile;

/**
 * 存储文件应用服务
 * <p>
 * 统一编排通用上传、批量上传、分片上传、预览和下载流程，让控制器只保留 HTTP 路由职责
 *
 * @author 955Kane
 */
@Service
public class StorageFileApplicationService
{
    private static final Logger log = LoggerFactory.getLogger(StorageFileApplicationService.class);

    /**
     * X File Storage 上传适配工具
     */
    private final XFileStorageUtils xFileStorageUtils;

    /**
     * 存储文件记录服务
     */
    private final ISysStorageFileService storageFileService;

    /**
     * 存储文件访问权限策略服务
     */
    private final StorageFileAccessPolicyService storageFileAccessPolicyService;

    /**
     * 存储文件响应组装器
     */
    private final StorageFileResponseAssembler storageFileResponseAssembler;

    /**
     * 构造存储文件应用服务
     *
     * @param xFileStorageUtils X File Storage 上传适配工具
     * @param storageFileService 存储文件记录服务
     * @param storageFileAccessPolicyService 存储文件访问权限策略服务
     * @param storageFileResponseAssembler 存储文件响应组装器
     */
    public StorageFileApplicationService(XFileStorageUtils xFileStorageUtils, ISysStorageFileService storageFileService,
            StorageFileAccessPolicyService storageFileAccessPolicyService,
            StorageFileResponseAssembler storageFileResponseAssembler)
    {
        this.xFileStorageUtils = xFileStorageUtils;
        this.storageFileService = storageFileService;
        this.storageFileAccessPolicyService = storageFileAccessPolicyService;
        this.storageFileResponseAssembler = storageFileResponseAssembler;
    }

    /**
     * 上传单个文件
     *
     * @param file 前端上传的文件
     * @param platform 可选存储平台标识
     * @param uploadScene 可选上传场景编码
     * @param thumbnail 是否生成缩略图
     * @param thumbnailWidth 缩略图宽度
     * @param thumbnailHeight 缩略图高度
     * @return 上传响应 DTO
     * @throws Exception 上传或校验失败时抛出
     */
    public StorageFileUploadRespDTO uploadFile(MultipartFile file, String platform, String uploadScene,
            Boolean thumbnail, Integer thumbnailWidth, Integer thumbnailHeight) throws Exception
    {
        // 上传动作仍交给适配工具处理，应用服务只负责串联上传结果和响应组装
        FileInfo fileInfo = xFileStorageUtils.upload(file, platform, uploadScene, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION,
                Boolean.TRUE.equals(thumbnail), thumbnailWidth, thumbnailHeight);
        return buildUploadRespDTO(fileInfo, uploadScene, file == null ? null : file.getOriginalFilename());
    }

    /**
     * 批量上传文件
     *
     * @param files 前端上传的文件集合
     * @param platform 可选存储平台标识
     * @param uploadScene 可选上传场景编码
     * @param thumbnail 是否生成缩略图
     * @param thumbnailWidth 缩略图宽度
     * @param thumbnailHeight 缩略图高度
     * @return 文件响应 DTO 集合
     * @throws Exception 上传或校验失败时抛出
     */
    public List<StorageFileUploadRespDTO> uploadFiles(List<MultipartFile> files, String platform, String uploadScene,
            Boolean thumbnail, Integer thumbnailWidth, Integer thumbnailHeight) throws Exception
    {
        List<String> uploadedUrls = new ArrayList<String>();
        try
        {
            // 批量数量优先读取上传场景配置，场景未配置时回退到全局配置
            xFileStorageUtils.assertBatchUploadAllowed(files, uploadScene);
            List<StorageFileUploadRespDTO> fileList = new ArrayList<StorageFileUploadRespDTO>();
            for (MultipartFile file : files)
            {
                // 批量上传没有跨对象存储事务，任一文件失败时由 catch 做已上传文件补偿删除
                FileInfo fileInfo = xFileStorageUtils.upload(file, platform, uploadScene,
                        MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION, Boolean.TRUE.equals(thumbnail), thumbnailWidth,
                        thumbnailHeight);
                uploadedUrls.add(fileInfo.getUrl());
                fileList.add(buildUploadRespDTO(fileInfo, uploadScene, file.getOriginalFilename()));
            }
            return fileList;
        }
        catch (Exception e)
        {
            // 已成功上传的文件需要尽快补偿删除，避免等待临时文件清理任务才释放对象存储资源
            compensateUploadedFiles(uploadedUrls);
            throw e;
        }
    }

    /**
     * 查询平台是否支持手动分片上传
     *
     * @param platform 可选存储平台标识
     * @return 分片上传支持信息
     */
    public MultipartUploadSupportInfo multipartSupport(String platform)
    {
        return xFileStorageUtils.isSupportMultipartUpload(platform);
    }

    /**
     * 初始化手动分片上传
     *
     * @param originalFilename 原始文件名
     * @param size 文件总大小，单位字节
     * @param contentType 文件内容类型
     * @param platform 可选存储平台标识
     * @param uploadScene 可选上传场景编码
     * @return 分片上传会话响应 DTO
     * @throws Exception 初始化失败时抛出
     */
    public StorageFileUploadRespDTO initiateMultipartUpload(String originalFilename, Long size, String contentType,
            String platform, String uploadScene) throws Exception
    {
        // 初始化阶段只创建上传会话和文件记录，不上传实际文件内容
        FileInfo fileInfo = xFileStorageUtils.initiateMultipartUpload(originalFilename, size, contentType, platform,
                uploadScene, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
        return buildUploadRespDTO(fileInfo, uploadScene, originalFilename);
    }

    /**
     * 上传一个文件分片
     *
     * @param fileId 文件ID，优先使用
     * @param url 文件真实地址，仅兼容旧前端
     * @param partNumber 分片编号
     * @param file 当前分片文件
     * @return 分片上传响应 DTO
     */
    public StorageFilePartRespDTO uploadPart(Long fileId, String url, Integer partNumber, MultipartFile file)
    {
        // 分片后续动作必须先校验上传会话归属，避免用户操作他人的未完成上传
        checkMultipartOwner(fileId, url);
        FilePartInfo filePartInfo = xFileStorageUtils.uploadPart(getMultipartFileInfo(fileId, url), partNumber, file);
        return StorageFilePartRespDTO.from(filePartInfo);
    }

    /**
     * 查询已经上传的分片列表
     *
     * @param fileId 文件ID，优先使用
     * @param url 文件真实地址，仅兼容旧前端
     * @param maxParts 本次最多返回的分片数量
     * @param partNumberMarker 分页起始分片编号
     * @return 若依统一响应对象
     */
    public AjaxResult listParts(Long fileId, String url, Integer maxParts, Integer partNumberMarker)
    {
        // 部分平台不支持列举分片，具体能力由 x-file-storage 平台适配器决定
        checkMultipartOwner(fileId, url);
        FilePartInfoList filePartInfoList = xFileStorageUtils.listParts(getMultipartFileInfo(fileId, url), maxParts,
                partNumberMarker);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("file", buildUploadRespDTO(filePartInfoList.getFileInfo(), null, null));
        ajax.put("partList", toFilePartList(filePartInfoList.getList()));
        ajax.put("maxParts", filePartInfoList.getMaxParts());
        ajax.put("truncated", filePartInfoList.getIsTruncated());
        ajax.put("partNumberMarker", filePartInfoList.getPartNumberMarker());
        ajax.put("nextPartNumberMarker", filePartInfoList.getNextPartNumberMarker());
        return ajax;
    }

    /**
     * 完成手动分片上传
     *
     * @param fileId 文件ID，优先使用
     * @param url 文件真实地址，仅兼容旧前端
     * @return 合并后的文件响应 DTO
     */
    public StorageFileUploadRespDTO completeMultipartUpload(Long fileId, String url)
    {
        // 合并成功后框架会更新主文件记录并删除分片记录
        checkMultipartOwner(fileId, url);
        FileInfo fileInfo = xFileStorageUtils.completeMultipartUpload(getMultipartFileInfo(fileId, url));
        return buildUploadRespDTO(fileInfo, null, null);
    }

    /**
     * 取消手动分片上传
     *
     * @param fileId 文件ID，优先使用
     * @param url 文件真实地址，仅兼容旧前端
     * @return 取消后的文件响应 DTO
     */
    public StorageFileUploadRespDTO abortMultipartUpload(Long fileId, String url)
    {
        // 取消成功后框架会通知平台释放未合并分片
        checkMultipartOwner(fileId, url);
        FileInfo fileInfo = xFileStorageUtils.abortMultipartUpload(getMultipartFileInfo(fileId, url));
        return buildUploadRespDTO(fileInfo, null, null);
    }

    /**
     * 写出文件内容
     * <p>
     * 该方法只校验文件 token 并输出文件流，业务菜单权限和数据权限仍由业务查询接口负责
     *
     * @param fileId 文件ID
     * @param thumbnail true 表示写出缩略图
     * @param attachment true 表示作为附件下载
     * @param accessToken 文件短期访问 token
     * @param response 响应对象
     */
    public void writeStorageFile(Long fileId, boolean thumbnail, boolean attachment, String accessToken,
            HttpServletResponse response)
    {
        SysStorageFile storageFile = storageFileService.selectStorageFileById(fileId);
        try
        {
            // 访问策略只判断文件访问本身，不反查具体业务表
            storageFileAccessPolicyService.checkAccessAllowed(storageFile, accessToken);
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
     * 构建上传返回 DTO
     *
     * @param fileInfo X File Storage 文件信息
     * @param uploadScene 上传场景编码
     * @param originalFilename 原始文件名兜底值
     * @return 上传响应 DTO
     */
    private StorageFileUploadRespDTO buildUploadRespDTO(FileInfo fileInfo, String uploadScene, String originalFilename)
    {
        if (fileInfo == null || StringUtils.isBlank(fileInfo.getUrl()))
        {
            return StorageFileUploadRespDTO.from(fileInfo, uploadScene, originalFilename);
        }
        // 上传成功后 FileRecorder 已经写入文件表，这里按 URL 反查 fileId、access 和对象 Key
        SysStorageFile storageFile = storageFileService.selectStorageFileByUrl(fileInfo.getUrl());
        return storageFileResponseAssembler.assemble(fileInfo, storageFile, uploadScene, originalFilename);
    }

    /**
     * 获取分片上传会话文件信息
     *
     * @param fileId 文件ID，优先使用
     * @param url 文件真实地址，仅兼容旧前端
     * @return X File Storage 文件信息
     */
    private FileInfo getMultipartFileInfo(Long fileId, String url)
    {
        if (fileId != null)
        {
            // fileId 不受代理 URL、临时 token 或访问域名变更影响，作为分片后续操作的主定位条件
            SysStorageFile storageFile = storageFileService.selectStorageFileById(fileId);
            if (storageFile == null)
            {
                throw new ServiceException("文件记录不存在");
            }
            FileInfo fileInfo = storageFileService.toFileInfo(storageFile);
            if (fileInfo == null)
            {
                throw new ServiceException("文件记录不存在");
            }
            return fileInfo;
        }
        if (StringUtils.isBlank(url))
        {
            throw new ServiceException("文件ID不能为空");
        }
        // URL 版本只兼容旧前端，新业务优先使用 fileId
        return xFileStorageUtils.getFileInfoByUrl(url);
    }

    /**
     * 校验分片上传会话归属
     *
     * @param fileId 文件ID，优先使用
     * @param url 文件真实地址，仅兼容旧前端
     */
    private void checkMultipartOwner(Long fileId, String url)
    {
        if (fileId != null)
        {
            // 新前端按 fileId 校验，避免代理 URL 无法反查真实上传记录
            storageFileService.checkMultipartOwner(fileId);
            return;
        }
        // 旧前端保留 URL 校验能力
        storageFileService.checkMultipartOwner(url);
    }

    /**
     * 将分片列表转换为接口返回 DTO 列表
     *
     * @param filePartInfos X File Storage 分片列表
     * @return 分片返回 DTO 列表
     */
    private List<StorageFilePartRespDTO> toFilePartList(List<FilePartInfo> filePartInfos)
    {
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
