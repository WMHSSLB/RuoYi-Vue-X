package com.ruoyi.system.service;

import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.StorageFileConstants;
import com.ruoyi.common.enums.StorageFileAccess;
import com.ruoyi.common.enums.StorageFileStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.SysStorageFile;

/**
 * 存储文件访问权限策略服务
 * <p>
 * 统一收敛公开文件、私有 token、临时文件、头像和文件管理员兜底下载规则
 *
 * @author 955Kane
 */
@Service
public class StorageFileAccessPolicyService
{
    /**
     * 存储文件短期访问授权服务
     */
    private final StorageFileAccessTokenService storageFileAccessTokenService;

    /**
     * 文件访问策略链
     */
    private final List<StorageFileAccessPolicy> accessPolicies;

    /**
     * 构造存储文件访问权限策略服务
     *
     * @param storageFileAccessTokenService 存储文件短期访问授权服务
     */
    public StorageFileAccessPolicyService(StorageFileAccessTokenService storageFileAccessTokenService)
    {
        this.storageFileAccessTokenService = storageFileAccessTokenService;
        this.accessPolicies = Arrays.asList(this::rejectMissingOrInvalidFile, this::allowPublicFile,
                this::allowValidAccessToken, this::rejectBoundBusinessPrivateFile, this::allowStorageManager,
                this::allowTemporaryOwner, this::allowAvatarOwner);
    }

    /**
     * 校验当前请求是否允许访问文件
     *
     * @param storageFile 存储文件记录
     * @param accessToken 文件访问 token
     */
    public void checkAccessAllowed(SysStorageFile storageFile, String accessToken)
    {
        StorageFileAccessContext context = new StorageFileAccessContext(storageFile, accessToken);
        for (StorageFileAccessPolicy accessPolicy : accessPolicies)
        {
            // 每个策略只处理自己关心的访问场景，未命中则交给下一个策略
            StorageFileAccessDecision decision = accessPolicy.decide(context);
            if (decision.isAllowed())
            {
                return;
            }
            if (decision.isDenied())
            {
                throw new ServiceException(decision.getMessage());
            }
        }
        throw new ServiceException("没有权限访问文件");
    }

    /**
     * 判断文件是否是私有访问文件
     *
     * @param storageFile 存储文件记录
     * @return true 表示私有文件
     */
    public boolean isPrivateStorageFile(SysStorageFile storageFile)
    {
        // 私有文件必须通过后端代理和短期 token 访问，不能把真实对象存储地址暴露给前端
        return storageFile != null && StorageFileAccess.isPrivate(storageFile.getAccess());
    }

    /**
     * 判断文件是否是公开访问文件
     *
     * @param storageFile 存储文件记录
     * @return true 表示公开文件
     */
    public boolean isPublicStorageFile(SysStorageFile storageFile)
    {
        // 访问级别以文件记录表 access 字段为准，不再在下载鉴权时解析存储路径
        return storageFile != null && StorageFileAccess.isPublic(storageFile.getAccess());
    }

    /**
     * 拒绝不存在或已失效文件
     *
     * @param context 文件访问上下文
     * @return 访问决策
     */
    private StorageFileAccessDecision rejectMissingOrInvalidFile(StorageFileAccessContext context)
    {
        if (context.getStorageFile() == null)
        {
            return StorageFileAccessDecision.deny("文件记录不存在");
        }
        if (StorageFileStatus.isInvalid(context.getStorageFile().getFileStatus()))
        {
            return StorageFileAccessDecision.deny("文件已失效");
        }
        return StorageFileAccessDecision.pass();
    }

    /**
     * 允许公开文件访问
     *
     * @param context 文件访问上下文
     * @return 访问决策
     */
    private StorageFileAccessDecision allowPublicFile(StorageFileAccessContext context)
    {
        if (isPublicStorageFile(context.getStorageFile()))
        {
            return StorageFileAccessDecision.allow();
        }
        return StorageFileAccessDecision.pass();
    }

    /**
     * 允许携带有效 token 的访问
     *
     * @param context 文件访问上下文
     * @return 访问决策
     */
    private StorageFileAccessDecision allowValidAccessToken(StorageFileAccessContext context)
    {
        if (storageFileAccessTokenService.verifyToken(context.getStorageFile().getFileId(), context.getAccessToken()))
        {
            return StorageFileAccessDecision.allow();
        }
        return StorageFileAccessDecision.pass();
    }

    /**
     * 拒绝缺少 token 的普通业务私有文件
     *
     * @param context 文件访问上下文
     * @return 访问决策
     */
    private StorageFileAccessDecision rejectBoundBusinessPrivateFile(StorageFileAccessContext context)
    {
        SysStorageFile storageFile = context.getStorageFile();
        if (StringUtils.isNotBlank(storageFile.getBizType())
                && !StorageFileConstants.BIZ_TYPE_AVATAR.equals(storageFile.getBizType()))
        {
            return StorageFileAccessDecision.deny("业务私有文件缺少访问授权");
        }
        return StorageFileAccessDecision.pass();
    }

    /**
     * 允许文件管理员访问非业务私有文件
     *
     * @param context 文件访问上下文
     * @return 访问决策
     */
    private StorageFileAccessDecision allowStorageManager(StorageFileAccessContext context)
    {
        if (hasStorageDownloadPermission())
        {
            return StorageFileAccessDecision.allow();
        }
        return StorageFileAccessDecision.pass();
    }

    /**
     * 允许上传人访问未绑定业务的临时文件
     *
     * @param context 文件访问上下文
     * @return 访问决策
     */
    private StorageFileAccessDecision allowTemporaryOwner(StorageFileAccessContext context)
    {
        SysStorageFile storageFile = context.getStorageFile();
        String username = getCurrentUsernameQuietly();
        if (StringUtils.isBlank(storageFile.getBizType()) && StringUtils.isNotBlank(storageFile.getCreateBy())
                && storageFile.getCreateBy().equals(username))
        {
            return StorageFileAccessDecision.allow();
        }
        return StorageFileAccessDecision.pass();
    }

    /**
     * 允许当前用户访问自己的头像文件
     *
     * @param context 文件访问上下文
     * @return 访问决策
     */
    private StorageFileAccessDecision allowAvatarOwner(StorageFileAccessContext context)
    {
        SysStorageFile storageFile = context.getStorageFile();
        Long userId = getCurrentUserIdQuietly();
        if (StorageFileConstants.BIZ_TYPE_AVATAR.equals(storageFile.getBizType())
                && userId != null && String.valueOf(userId).equals(storageFile.getBizId()))
        {
            return StorageFileAccessDecision.allow();
        }
        return StorageFileAccessDecision.pass();
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
     * 获取当前登录用户名
     *
     * @return 当前登录用户名，未登录时返回 null
     */
    private String getCurrentUsernameQuietly()
    {
        try
        {
            return SecurityUtils.getUsername();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 当前登录用户ID，未登录时返回 null
     */
    private Long getCurrentUserIdQuietly()
    {
        try
        {
            return SecurityUtils.getUserId();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 存储文件访问策略
     */
    private interface StorageFileAccessPolicy
    {
        /**
         * 根据上下文作出访问决策
         *
         * @param context 文件访问上下文
         * @return 访问决策
         */
        StorageFileAccessDecision decide(StorageFileAccessContext context);
    }

    /**
     * 存储文件访问上下文
     */
    private static class StorageFileAccessContext
    {
        /**
         * 存储文件记录
         */
        private final SysStorageFile storageFile;

        /**
         * 文件访问 token
         */
        private final String accessToken;

        StorageFileAccessContext(SysStorageFile storageFile, String accessToken)
        {
            this.storageFile = storageFile;
            this.accessToken = accessToken;
        }

        public SysStorageFile getStorageFile()
        {
            return storageFile;
        }

        public String getAccessToken()
        {
            return accessToken;
        }
    }

    /**
     * 存储文件访问决策
     */
    private static class StorageFileAccessDecision
    {
        /**
         * 是否允许访问
         */
        private final boolean allowed;

        /**
         * 是否拒绝访问
         */
        private final boolean denied;

        /**
         * 拒绝原因
         */
        private final String message;

        private StorageFileAccessDecision(boolean allowed, boolean denied, String message)
        {
            this.allowed = allowed;
            this.denied = denied;
            this.message = message;
        }

        public static StorageFileAccessDecision allow()
        {
            return new StorageFileAccessDecision(true, false, null);
        }

        public static StorageFileAccessDecision deny(String message)
        {
            return new StorageFileAccessDecision(false, true, message);
        }

        public static StorageFileAccessDecision pass()
        {
            return new StorageFileAccessDecision(false, false, null);
        }

        public boolean isAllowed()
        {
            return allowed;
        }

        public boolean isDenied()
        {
            return denied;
        }

        public String getMessage()
        {
            return message;
        }
    }
}
