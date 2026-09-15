package com.ruoyi.common.utils.file;

import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.upload.FilePartInfo;
import org.dromara.x.file.storage.core.upload.FilePartInfoList;
import org.dromara.x.file.storage.core.upload.InitiateMultipartUploadPretreatment;
import org.dromara.x.file.storage.core.upload.ListPartsPretreatment;
import org.dromara.x.file.storage.core.upload.MultipartUploadSupportInfo;
import org.dromara.x.file.storage.core.upload.UploadPretreatment;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.exception.file.FileNameLengthLimitExceededException;
import com.ruoyi.common.exception.file.FileSizeLimitExceededException;
import com.ruoyi.common.exception.file.InvalidExtensionException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.StorageUploadScenePolicyService.StorageUploadScenePolicy;
import com.ruoyi.common.utils.uuid.IdUtils;

/**
 * X File Storage 上传适配工具
 * <p>
 * 控制器优先调用该工具，集中维护平台选择、上传校验、文件命名、场景路径、ACL、缩略图和分片上传逻辑
 *
 * @author 955Kane
 */
@Component
public class XFileStorageUtils
{
    /**
     * X File Storage 自动配置提供的统一文件存储服务
     */
    private final FileStorageService fileStorageService;

    /**
     * 上传场景策略服务
     */
    private final StorageUploadScenePolicyService storageUploadScenePolicyService;

    /**
     * 构造上传适配工具
     *
     * @param fileStorageService X File Storage 统一存储服务
     * @param storageUploadScenePolicyService 上传场景策略服务
     */
    public XFileStorageUtils(FileStorageService fileStorageService,
            StorageUploadScenePolicyService storageUploadScenePolicyService)
    {
        this.fileStorageService = fileStorageService;
        this.storageUploadScenePolicyService = storageUploadScenePolicyService;
    }

    /**
     * 使用默认平台和默认扩展名白名单上传文件
     *
     * @param file 前端提交的 Multipart 文件
     * @return 存储平台返回的文件元数据
     * @throws Exception 校验失败或上传失败时抛出
     */
    public FileInfo upload(MultipartFile file) throws Exception
    {
        // 默认上传入口统一委托到完整参数方法
        return upload(file, null, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
    }

    /**
     * 使用默认平台和指定扩展名白名单上传文件
     *
     * @param file 前端提交的 Multipart 文件
     * @param allowedExtension 允许的扩展名数组，不带点号
     * @return 存储平台返回的文件元数据
     * @throws Exception 校验失败或上传失败时抛出
     */
    public FileInfo upload(MultipartFile file, String[] allowedExtension) throws Exception
    {
        // 图片上传等场景会传入更严格的扩展名白名单
        return upload(file, null, allowedExtension);
    }

    /**
     * 使用可选平台和默认扩展名白名单上传文件
     *
     * @param file 前端提交的 Multipart 文件
     * @param platform 可选平台标识，为空时使用配置中的默认平台
     * @return 存储平台返回的文件元数据
     * @throws Exception 校验失败或上传失败时抛出
     */
    public FileInfo upload(MultipartFile file, String platform) throws Exception
    {
        // 指定平台但不指定白名单时使用通用白名单
        return upload(file, platform, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
    }

    /**
     * 使用可选平台和指定扩展名白名单上传文件
     *
     * @param file 前端提交的 Multipart 文件
     * @param platform 可选平台标识，为空时使用配置中的默认平台
     * @param allowedExtension 允许的扩展名数组，不带点号
     * @return 存储平台返回的文件元数据
     * @throws Exception 校验失败或上传失败时抛出
     */
    public FileInfo upload(MultipartFile file, String platform, String[] allowedExtension) throws Exception
    {
        // 默认不生成缩略图，保持普通上传行为轻量
        return upload(file, platform, allowedExtension, false, null, null);
    }

    /**
     * 使用可选平台和指定扩展名白名单上传文件，并可按需生成缩略图
     *
     * @param file 前端提交的 Multipart 文件
     * @param platform 可选平台标识，为空时使用配置中的默认平台
     * @param allowedExtension 允许的扩展名数组，不带点号
     * @param thumbnail true 表示生成缩略图
     * @param thumbnailWidth 缩略图宽度，宽高同时为空时使用框架默认策略
     * @param thumbnailHeight 缩略图高度，宽高同时为空时使用框架默认策略
     * @return 存储平台返回的文件元数据
     * @throws Exception 校验失败、缩略图生成失败或上传失败时抛出
     */
    public FileInfo upload(MultipartFile file, String platform, String[] allowedExtension, boolean thumbnail,
            Integer thumbnailWidth, Integer thumbnailHeight) throws Exception
    {
        // 未指定上传场景时使用调用方传入的白名单、大小和缩略图规则
        return upload(file, platform, null, allowedExtension, thumbnail, thumbnailWidth, thumbnailHeight);
    }

    /**
     * 使用可选平台和上传场景上传文件，并可按需生成缩略图
     *
     * @param file 前端提交的 Multipart 文件
     * @param platform 可选平台标识，为空时使用配置中的默认平台
     * @param uploadScene 可选上传场景编码
     * @param allowedExtension 默认允许的扩展名数组，不带点号
     * @param thumbnail true 表示生成缩略图
     * @param thumbnailWidth 缩略图宽度，宽高同时为空时使用框架默认策略
     * @param thumbnailHeight 缩略图高度，宽高同时为空时使用框架默认策略
     * @return 存储平台返回的文件元数据
     * @throws Exception 校验失败、缩略图生成失败或上传失败时抛出
     */
    public FileInfo upload(MultipartFile file, String platform, String uploadScene, String[] allowedExtension,
            boolean thumbnail, Integer thumbnailWidth, Integer thumbnailHeight) throws Exception
    {
        // 日期目录保持 yyyy/MM/dd，便于按天归档
        String datePath = DateUtils.datePath();
        // 上传场景策略服务统一解析大小、格式、路径、缩略图和访问级别
        StorageUploadScenePolicy policy = storageUploadScenePolicyService.resolvePolicy(uploadScene, allowedExtension,
                thumbnail, thumbnailWidth, thumbnailHeight, datePath);
        // 先执行若依原有文件安全校验，校验规则来自最终场景策略
        assertAllowed(file, policy.getAllowedExtension(), policy.getMaxSize());
        // 构造上传预处理对象，文件名继续使用若依 UUID 规则
        UploadPretreatment upload = fileStorageService.of(file)
                .setPath(policy.getStoragePath())
                .setSaveFilename(FileUploadUtils.uuidFilename(file).substring(datePath.length() + 1));
        if (StringUtils.isNotBlank(platform))
        {
            // 指定平台时覆盖配置中的默认平台
            upload.setPlatform(platform);
        }
        // ACL 是否设置由场景策略服务决定，未声明场景时保留平台默认权限
        storageUploadScenePolicyService.applyAcl(upload, policy);
        if (policy.isThumbnail())
        {
            // 明确要求缩略图时再开启，避免普通文件被误处理为图片
            setThumbnail(upload, file, policy.getThumbnailWidth(), policy.getThumbnailHeight(),
                    policy.getThumbnailSuffix());
        }
        // platform 为空时 X File Storage 会使用 dromara.x-file-storage.default-platform
        return upload.upload();
    }

    /**
     * 校验批量上传文件数量
     * <p>
     * 优先使用上传场景配置，其次使用全局配置，最后使用系统兜底值
     *
     * @param files 前端提交的文件集合
     * @param uploadScene 可选上传场景编码
     */
    public void assertBatchUploadAllowed(List<MultipartFile> files, String uploadScene)
    {
        // 通用上传入口不传代码级限制时完全按配置文件决定批量数量
        assertBatchUploadAllowed(files, uploadScene, null);
    }

    /**
     * 校验批量上传文件数量
     * <p>
     * 后端业务代码可以通过 customMaxBatchUploadCount 覆盖配置文件中的场景值和全局值
     *
     * @param files 前端提交的文件集合
     * @param uploadScene 可选上传场景编码
     * @param customMaxBatchUploadCount 代码指定的最大文件数量
     */
    public void assertBatchUploadAllowed(List<MultipartFile> files, String uploadScene, Integer customMaxBatchUploadCount)
    {
        // 批量上传必须至少包含一个文件，避免空请求返回成功造成前端误判
        if (files == null || files.isEmpty())
        {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        // 获取上传场景时会顺带执行 require-upload-scene 和场景存在性校验
        // 数量优先级为代码指定值、场景配置值、全局配置值、系统兜底值
        int maxBatchUploadCount = storageUploadScenePolicyService.resolveMaxBatchUploadCount(uploadScene,
                customMaxBatchUploadCount);
        if (files.size() > maxBatchUploadCount)
        {
            throw new IllegalArgumentException("单次最多上传 " + maxBatchUploadCount + " 个文件");
        }
    }

    /**
     * 按 URL 删除远程对象
     *
     * @param url X File Storage 上传后返回的文件 URL
     * @return true 表示存储平台确认删除成功
     */
    public boolean delete(String url)
    {
        // 空 URL 不交给存储服务处理
        if (StringUtils.isBlank(url))
        {
            return false;
        }
        // 先根据 URL 还原 FileInfo，尽量让框架同时处理缩略图
        FileInfo fileInfo = fileStorageService.getFileInfoByUrl(url);
        if (fileInfo != null)
        {
            return fileStorageService.delete(fileInfo);
        }
        // 没有记录时认为无需删除，避免历史 URL 影响主业务
        return true;
    }

    /**
     * 根据 URL 获取文件信息
     *
     * @param url X File Storage 上传后返回的文件 URL
     * @return 文件信息
     */
    public FileInfo getFileInfoByUrl(String url)
    {
        // URL 为空时无法定位文件记录
        if (StringUtils.isBlank(url))
        {
            throw new IllegalArgumentException("文件 URL 不能为空");
        }
        // 通过 FileRecorder 从数据库还原 FileInfo
        FileInfo fileInfo = fileStorageService.getFileInfoByUrl(url);
        if (fileInfo == null)
        {
            throw new IllegalArgumentException("文件记录不存在");
        }
        return fileInfo;
    }

    /**
     * 下载文件内容
     *
     * @param fileInfo 文件信息
     * @return 文件字节内容
     */
    public byte[] downloadBytes(FileInfo fileInfo)
    {
        // 下载必须依赖完整 FileInfo，里面包含平台、路径和文件名
        if (fileInfo == null)
        {
            throw new IllegalArgumentException("文件信息不能为空");
        }
        // 由 X File Storage 根据 FileInfo 中的平台信息选择实际存储平台下载
        return fileStorageService.download(fileInfo).bytes();
    }

    /**
     * 查询指定平台是否支持手动分片上传
     *
     * @param platform 可选平台标识，为空时使用配置中的默认平台
     * @return 分片上传支持信息
     */
    public MultipartUploadSupportInfo isSupportMultipartUpload(String platform)
    {
        // platform 为空时查询默认平台的分片上传能力
        if (StringUtils.isBlank(platform))
        {
            return fileStorageService.isSupportMultipartUpload();
        }
        // platform 不为空时查询指定平台的分片上传能力
        return fileStorageService.isSupportMultipartUpload(platform);
    }

    /**
     * 初始化手动分片上传
     *
     * @param originalFilename 原始文件名
     * @param size 文件总大小，单位字节
     * @param contentType 文件内容类型
     * @param platform 可选平台标识，为空时使用配置中的默认平台
     * @param allowedExtension 允许的扩展名数组，不带点号
     * @return 初始化后的文件信息
     * @throws FileNameLengthLimitExceededException 原始文件名过长时抛出
     * @throws FileSizeLimitExceededException 文件大小超过限制时抛出
     * @throws InvalidExtensionException 扩展名不在白名单内时抛出
     */
    public FileInfo initiateMultipartUpload(String originalFilename, Long size, String contentType, String platform,
            String[] allowedExtension) throws FileNameLengthLimitExceededException, FileSizeLimitExceededException,
            InvalidExtensionException
    {
        // 未指定上传场景时使用调用方传入的白名单和默认大小规则
        return initiateMultipartUpload(originalFilename, size, contentType, platform, null, allowedExtension);
    }

    /**
     * 初始化手动分片上传
     *
     * @param originalFilename 原始文件名
     * @param size 文件总大小，单位字节
     * @param contentType 文件内容类型
     * @param platform 可选平台标识，为空时使用配置中的默认平台
     * @param uploadScene 可选上传场景编码
     * @param allowedExtension 默认允许的扩展名数组，不带点号
     * @return 初始化后的文件信息
     * @throws FileNameLengthLimitExceededException 原始文件名过长时抛出
     * @throws FileSizeLimitExceededException 文件大小超过限制时抛出
     * @throws InvalidExtensionException 扩展名不在白名单内时抛出
     */
    public FileInfo initiateMultipartUpload(String originalFilename, Long size, String contentType, String platform,
            String uploadScene, String[] allowedExtension) throws FileNameLengthLimitExceededException,
            FileSizeLimitExceededException, InvalidExtensionException
    {
        // 日期目录保持 yyyy/MM/dd，和普通上传使用相同归档规则
        String datePath = DateUtils.datePath();
        // 分片初始化复用上传场景策略，保证大小、格式、路径和 ACL 与普通上传一致
        StorageUploadScenePolicy policy = storageUploadScenePolicyService.resolvePolicy(uploadScene, allowedExtension,
                false, null, null, datePath);
        // 初始化阶段还没有完整文件内容，只能先校验文件名、扩展名和声明大小
        assertInitiateAllowed(originalFilename, size, policy.getAllowedExtension(), policy.getMaxSize());
        // 分片上传使用 UUID 文件名，避免暴露用户原始文件名并降低重名风险
        String saveFilename = IdUtils.fastSimpleUUID() + "." + FilenameUtils.getExtension(originalFilename);
        // 构造初始化请求，返回的 FileInfo 会携带 uploadId 供后续分片上传使用
        InitiateMultipartUploadPretreatment upload = fileStorageService.initiateMultipartUpload()
                .setPath(policy.getStoragePath())
                .setSaveFilename(saveFilename)
                .setOriginalFilename(originalFilename)
                .setSize(size)
                .setContentType(contentType);
        if (StringUtils.isNotBlank(platform))
        {
            // 指定平台时覆盖配置中的默认平台
            upload.setPlatform(platform);
        }
        // ACL 是否设置由场景策略服务决定，未声明场景时保留平台默认权限
        storageUploadScenePolicyService.applyMultipartAcl(upload, policy);
        // 执行初始化，框架会通过 FileRecorder.save 保存分片上传会话
        return upload.init();
    }

    /**
     * 上传一个文件分片
     *
     * @param url 初始化分片上传后返回的文件地址
     * @param partNumber 分片编号
     * @param file 当前分片文件
     * @return 已上传的分片信息
     */
    public FilePartInfo uploadPart(String url, Integer partNumber, MultipartFile file)
    {
        // URL 版本保留给旧前端，内部统一转为 FileInfo 版本
        return uploadPart(getRequiredFileInfo(url), partNumber, file);
    }

    /**
     * 上传大文件分片
     *
     * @param fileInfo 分片上传文件信息
     * @param partNumber 分片编号，从 1 开始
     * @param file 当前分片文件
     * @return 已上传的分片信息
     */
    public FilePartInfo uploadPart(FileInfo fileInfo, Integer partNumber, MultipartFile file)
    {
        // 分片编号从 1 开始，非法编号直接拒绝
        if (partNumber == null || partNumber <= 0)
        {
            throw new IllegalArgumentException("分片编号必须大于 0");
        }
        // 分片内容不能为空，避免上传空分片污染分片记录
        if (file == null || file.isEmpty())
        {
            throw new IllegalArgumentException("分片文件不能为空");
        }
        // FileInfo 里面包含平台、路径和 uploadId，fileId 调用方可以避免代理 URL 反查失败
        FileInfo requiredFileInfo = getRequiredFileInfo(fileInfo);
        // 上传当前分片，框架成功后会通过 FileRecorder.saveFilePart 保存分片记录
        return fileStorageService.uploadPart(requiredFileInfo, partNumber, file, file.getSize()).upload();
    }

    /**
     * 查询已经上传的分片列表
     *
     * @param url 初始化分片上传后返回的文件地址
     * @param maxParts 本次最多返回的分片数量
     * @param partNumberMarker 分页起始分片编号
     * @return 分片列表
     */
    public FilePartInfoList listParts(String url, Integer maxParts, Integer partNumberMarker)
    {
        // URL 版本保留给旧前端，内部统一转为 FileInfo 版本
        return listParts(getRequiredFileInfo(url), maxParts, partNumberMarker);
    }

    /**
     * 查询已经上传的分片列表
     *
     * @param fileInfo 分片上传文件信息
     * @param maxParts 最多返回的分片数量
     * @param partNumberMarker 分页起始分片编号
     * @return 分片列表
     */
    public FilePartInfoList listParts(FileInfo fileInfo, Integer maxParts, Integer partNumberMarker)
    {
        // FileInfo 里面包含平台、路径和 uploadId，fileId 调用方可以避免代理 URL 反查失败
        FileInfo requiredFileInfo = getRequiredFileInfo(fileInfo);
        // 构造列举请求，由框架根据平台能力决定是否支持
        ListPartsPretreatment listParts = fileStorageService.listParts(requiredFileInfo);
        if (maxParts != null && maxParts > 0)
        {
            // 设置本次最多返回的分片数量
            listParts.setMaxParts(maxParts);
        }
        if (partNumberMarker != null && partNumberMarker > 0)
        {
            // 设置分页起始分片编号
            listParts.setPartNumberMarker(partNumberMarker);
        }
        return listParts.listParts();
    }

    /**
     * 完成手动分片上传
     *
     * @param url 初始化分片上传后返回的文件地址
     * @return 合并后的文件信息
     */
    public FileInfo completeMultipartUpload(String url)
    {
        // URL 版本保留给旧前端，内部统一转为 FileInfo 版本
        return completeMultipartUpload(getRequiredFileInfo(url));
    }

    /**
     * 完成手动分片上传
     *
     * @param fileInfo 分片上传文件信息
     * @return 合并后的文件信息
     */
    public FileInfo completeMultipartUpload(FileInfo fileInfo)
    {
        // FileInfo 里面包含平台、路径和 uploadId，fileId 调用方可以避免代理 URL 反查失败
        FileInfo requiredFileInfo = getRequiredFileInfo(fileInfo);
        // 通知存储平台合并分片，成功后框架会更新主文件记录并清理分片记录
        return fileStorageService.completeMultipartUpload(requiredFileInfo).complete();
    }

    /**
     * 取消手动分片上传
     *
     * @param url 初始化分片上传后返回的文件地址
     * @return 取消后的文件信息
     */
    public FileInfo abortMultipartUpload(String url)
    {
        // URL 版本保留给旧前端，内部统一转为 FileInfo 版本
        return abortMultipartUpload(getRequiredFileInfo(url));
    }

    /**
     * 取消手动分片上传
     *
     * @param fileInfo 分片上传文件信息
     * @return 取消后的文件信息
     */
    public FileInfo abortMultipartUpload(FileInfo fileInfo)
    {
        // FileInfo 里面包含平台、路径和 uploadId，fileId 调用方可以避免代理 URL 反查失败
        FileInfo requiredFileInfo = getRequiredFileInfo(fileInfo);
        // 通知存储平台取消分片上传，成功后框架会清理分片记录
        return fileStorageService.abortMultipartUpload(requiredFileInfo).abort();
    }

    /**
     * 复用若依本地上传的校验规则
     *
     * @param file 前端提交的 Multipart 文件
     * @param allowedExtension 允许的扩展名数组，不带点号
     * @param maxSize 最大上传大小，单位字节
     * @throws FileSizeLimitExceededException 文件超过大小限制时抛出
     * @throws InvalidExtensionException 扩展名不在白名单内时抛出
     * @throws FileNameLengthLimitExceededException 原始文件名过长时抛出
     */
    private void assertAllowed(MultipartFile file, String[] allowedExtension, long maxSize)
            throws FileSizeLimitExceededException, InvalidExtensionException, FileNameLengthLimitExceededException
    {
        // 切换存储平台不应放宽上传限制，所以沿用若依原有文件名、大小和扩展名校验
        int fileNameLength = file.getOriginalFilename() == null ? 0 : file.getOriginalFilename().length();
        if (fileNameLength > FileUploadUtils.DEFAULT_FILE_NAME_LENGTH)
        {
            // 原始文件名过长时抛出若依原有异常
            throw new FileNameLengthLimitExceededException(FileUploadUtils.DEFAULT_FILE_NAME_LENGTH);
        }
        // 文件大小和扩展名校验统一委托给若依原工具
        FileUploadUtils.assertAllowed(file, allowedExtension, maxSize);
    }

    /**
     * 校验分片上传初始化参数
     *
     * @param originalFilename 原始文件名
     * @param size 文件总大小，单位字节
     * @param allowedExtension 允许的扩展名数组，不带点号
     * @param maxSize 最大上传大小，单位字节
     * @throws FileNameLengthLimitExceededException 原始文件名过长时抛出
     * @throws InvalidExtensionException 扩展名不在白名单内时抛出
     */
    private void assertInitiateAllowed(String originalFilename, Long size, String[] allowedExtension, long maxSize)
            throws FileNameLengthLimitExceededException, InvalidExtensionException
    {
        // 原始文件名不能为空，否则无法判断扩展名和生成最终保存名
        if (StringUtils.isBlank(originalFilename))
        {
            throw new IllegalArgumentException("文件名不能为空");
        }
        // 分片上传声明大小必须大于 0，避免创建无意义的上传会话
        if (size == null || size <= 0)
        {
            throw new IllegalArgumentException("文件大小必须大于 0");
        }
        // 声明大小超过当前场景允许大小时直接拒绝初始化分片上传
        if (size > maxSize)
        {
            throw new FileSizeLimitExceededException((maxSize + 1024 * 1024 - 1) / 1024 / 1024);
        }
        // 复用若依原有文件名长度限制
        if (originalFilename.length() > FileUploadUtils.DEFAULT_FILE_NAME_LENGTH)
        {
            throw new FileNameLengthLimitExceededException(FileUploadUtils.DEFAULT_FILE_NAME_LENGTH);
        }
        // 初始化阶段按原始文件名后缀做白名单校验
        String extension = FilenameUtils.getExtension(originalFilename);
        if (allowedExtension != null && !FileUploadUtils.isAllowedExtension(extension, allowedExtension))
        {
            throw new InvalidExtensionException(allowedExtension, extension, originalFilename);
        }
    }

    /**
     * 根据 URL 获取必须存在的分片文件信息
     *
     * @param url 文件访问地址
     * @return 文件信息
     */
    private FileInfo getRequiredFileInfo(String url)
    {
        // URL 为空时无法定位分片上传会话
        if (StringUtils.isBlank(url))
        {
            throw new IllegalArgumentException("文件 URL 不能为空");
        }
        // 通过 FileRecorder 从数据库还原 FileInfo
        FileInfo fileInfo = fileStorageService.getFileInfoByUrl(url);
        if (fileInfo == null)
        {
            throw new IllegalArgumentException("文件记录不存在");
        }
        // 分片上传后续操作必须依赖 uploadId
        if (StringUtils.isBlank(fileInfo.getUploadId()))
        {
            throw new IllegalArgumentException("文件不是有效的分片上传会话");
        }
        return fileInfo;
    }

    /**
     * 获取必须存在且带 uploadId 的分片文件信息
     *
     * @param fileInfo 文件信息
     * @return 文件信息
     */
    private FileInfo getRequiredFileInfo(FileInfo fileInfo)
    {
        // fileId 链路已经在业务层查出 FileInfo，这里只做分片会话必要字段校验
        if (fileInfo == null)
        {
            throw new IllegalArgumentException("文件记录不存在");
        }
        if (StringUtils.isBlank(fileInfo.getUploadId()))
        {
            throw new IllegalArgumentException("文件不是有效的分片上传会话");
        }
        return fileInfo;
    }

    /**
     * 为上传任务设置缩略图参数
     *
     * @param upload 当前上传预处理对象
     * @param file 前端提交的 Multipart 文件
     * @param thumbnailWidth 缩略图宽度
     * @param thumbnailHeight 缩略图高度
     */
    private void setThumbnail(UploadPretreatment upload, MultipartFile file, Integer thumbnailWidth,
            Integer thumbnailHeight, String thumbnailSuffix)
    {
        // 缩略图只支持图片文件，避免 PDF、压缩包等普通文件进入图片处理流程
        if (!FileUploadUtils.isAllowedExtension(FileUploadUtils.getExtension(file), MimeTypeUtils.IMAGE_EXTENSION))
        {
            throw new IllegalArgumentException("缩略图只支持图片文件");
        }
        if (StringUtils.isNotBlank(thumbnailSuffix))
        {
            // 场景声明缩略图后缀时覆盖全局配置，例如头像用 .min.jpg，素材图可用 .min.png
            upload.setThumbnailSuffix(thumbnailSuffix);
        }
        // 宽高同时不传时使用 X File Storage 和 Thumbnailator 的默认缩略图策略
        if (thumbnailWidth == null && thumbnailHeight == null)
        {
            upload.thumbnail();
            return;
        }
        // 只传一个值或传入非正数时直接拒绝，避免生成尺寸不可预期
        if (thumbnailWidth == null || thumbnailHeight == null || thumbnailWidth <= 0 || thumbnailHeight <= 0)
        {
            throw new IllegalArgumentException("缩略图宽高必须同时大于 0");
        }
        // 指定宽高时按调用方要求生成缩略图
        upload.thumbnail(thumbnailWidth, thumbnailHeight);
    }
}
