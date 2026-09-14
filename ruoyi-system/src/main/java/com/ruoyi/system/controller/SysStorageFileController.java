package com.ruoyi.system.controller;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.x.file.storage.core.FileInfo;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.common.utils.file.XFileStorageUtils;
import com.ruoyi.system.domain.SysStorageFile;
import com.ruoyi.system.service.ISysStorageFileService;

/**
 * 存储文件管理
 * <p>
 * 提供文件资产列表、详情、下载和删除处理能力，业务文件下载仍应优先走业务模块专用接口
 *
 * @author 955Kane
 */
@RestController
@RequestMapping("/system/storage/file")
public class SysStorageFileController extends BaseController
{
    /**
     * 存储文件记录服务
     */
    private final ISysStorageFileService storageFileService;

    /**
     * X File Storage 上传下载工具
     */
    private final XFileStorageUtils xFileStorageUtils;

    /**
     * 构造存储文件管理控制器
     *
     * @param storageFileService 存储文件记录服务
     * @param xFileStorageUtils X File Storage 工具
     */
    public SysStorageFileController(ISysStorageFileService storageFileService, XFileStorageUtils xFileStorageUtils)
    {
        this.storageFileService = storageFileService;
        this.xFileStorageUtils = xFileStorageUtils;
    }

    /**
     * 查询存储文件列表
     *
     * @param storageFile 查询条件
     * @return 分页文件列表
     */
    @PreAuthorize("@ss.hasPermi('system:storage:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysStorageFile storageFile)
    {
        // 文件资产列表可能较大，继续沿用若依分页约定
        startPage();
        List<SysStorageFile> list = storageFileService.selectStorageFileList(storageFile);
        return getDataTable(list);
    }

    /**
     * 查询存储文件详情
     *
     * @param fileId 文件ID
     * @return 文件详情
     */
    @PreAuthorize("@ss.hasPermi('system:storage:query')")
    @GetMapping("/{fileId}")
    public AjaxResult getInfo(@PathVariable Long fileId)
    {
        return success(storageFileService.selectStorageFileById(fileId));
    }

    /**
     * 管理后台下载文件
     *
     * @param fileId 文件ID
     * @param response 响应对象
     */
    @PreAuthorize("@ss.hasPermi(T(com.ruoyi.common.constant.StorageFileConstants).PERMISSION_STORAGE_DOWNLOAD)")
    @GetMapping("/download/{fileId}")
    public void download(@PathVariable Long fileId, HttpServletResponse response)
    {
        SysStorageFile storageFile = storageFileService.selectStorageFileById(fileId);
        if (storageFile == null)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        FileInfo fileInfo = storageFileService.toFileInfo(storageFile);
        if (fileInfo == null)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try
        {
            // 管理后台下载经过权限控制，可用于排查公开和私有文件
            String downloadName = StringUtils.isNotBlank(fileInfo.getOriginalFilename()) ? fileInfo.getOriginalFilename()
                    : FileUtils.getName(fileInfo.getUrl());
            response.setContentType(StringUtils.isNotBlank(fileInfo.getContentType()) ? fileInfo.getContentType()
                    : MediaType.APPLICATION_OCTET_STREAM_VALUE);
            FileUtils.setAttachmentResponseHeader(response, downloadName);
            response.getOutputStream().write(xFileStorageUtils.downloadBytes(fileInfo));
        }
        catch (Exception e)
        {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 标记文件为待删除
     *
     * @param fileId 文件ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('system:storage:edit')")
    @Log(title = "存储文件", businessType = BusinessType.UPDATE)
    @PutMapping("/waitDelete/{fileId}")
    public AjaxResult waitDelete(@PathVariable Long fileId)
    {
        return toAjax(storageFileService.markStorageFileWaitDeleteById(fileId, SecurityUtils.getUsername()));
    }

    /**
     * 立即删除文件
     *
     * @param fileId 文件ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('system:storage:remove')")
    @Log(title = "存储文件", businessType = BusinessType.DELETE)
    @PostMapping("/deleteNow/{fileId}")
    public AjaxResult deleteNow(@PathVariable Long fileId)
    {
        return storageFileService.deleteStorageFileNow(fileId, SecurityUtils.getUsername()) ? success() : error("删除文件失败");
    }

    /**
     * 重试删除待删除文件
     *
     * @param fileId 文件ID
     * @return 操作结果
     */
    @PreAuthorize("@ss.hasPermi('system:storage:remove')")
    @Log(title = "存储文件", businessType = BusinessType.DELETE)
    @PostMapping("/retryDelete/{fileId}")
    public AjaxResult retryDelete(@PathVariable Long fileId)
    {
        return storageFileService.retryDeleteStorageFile(fileId, SecurityUtils.getUsername()) ? success() : error("删除文件失败");
    }
}
