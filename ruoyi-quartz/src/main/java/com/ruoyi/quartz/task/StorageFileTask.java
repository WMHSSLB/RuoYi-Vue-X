package com.ruoyi.quartz.task;

import java.util.List;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.ruoyi.system.domain.SysStorageFile;
import com.ruoyi.system.service.ISysStorageFileService;

/**
 * 存储文件清理任务
 * <p>
 * 定时清理超时临时文件和已经到期的待删除文件，避免对象存储长期堆积孤儿文件
 *
 * @author ruoyi
 */
@Component("storageFileTask")
public class StorageFileTask
{
    private static final Logger log = LoggerFactory.getLogger(StorageFileTask.class);

    private final ISysStorageFileService storageFileService;

    private final FileStorageService fileStorageService;

    /**
     * 构造存储文件清理任务
     *
     * @param storageFileService 存储文件记录服务
     * @param fileStorageService X File Storage 统一存储服务
     */
    public StorageFileTask(ISysStorageFileService storageFileService, FileStorageService fileStorageService)
    {
        this.storageFileService = storageFileService;
        this.fileStorageService = fileStorageService;
    }

    /**
     * 清理过期存储文件
     */
    public void clearStorageFile()
    {
        // 无参任务使用配置文件中的临时文件过期时间
        clearStorageFile(null);
    }

    /**
     * 清理过期存储文件
     *
     * @param tempExpireHours 临时文件过期小时数
     */
    public void clearStorageFile(Integer tempExpireHours)
    {
        // 未传入过期时间或参数非法时交给服务层读取 ruoyi.storage-file.temp-expire-hours
        int expireHours = tempExpireHours == null ? 0 : tempExpireHours;
        // 查询临时过期和待删除到期的文件记录
        List<SysStorageFile> storageFiles = storageFileService.selectCleanableStorageFiles(expireHours);
        for (SysStorageFile storageFile : storageFiles)
        {
            try
            {
                // 数据库记录还原为 FileInfo 后，框架才能定位具体平台和对象路径
                FileInfo fileInfo = storageFileService.toFileInfo(storageFile);
                if (fileInfo == null)
                {
                    continue;
                }
                // 删除原图和缩略图，成功后 FileRecorder.delete 会把记录标记为已删除
                fileStorageService.delete(fileInfo);
            }
            catch (Exception e)
            {
                // 单个文件删除失败不影响后续文件，避免一次异常中断整批清理
                log.warn("清理存储文件失败，url={}", storageFile.getUrl(), e);
            }
        }
    }
}
