package com.ruoyi.system.service;

import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.system.config.StorageFileProperties;

/**
 * 存储文件短期访问授权服务
 * <p>
 * 业务接口完成自身权限校验后调用本服务签发 token，通用文件接口只校验 token 是否能访问对应 fileId
 *
 * @author 955Kane
 */
@Service
public class StorageFileAccessTokenService
{
    /**
     * Redis 缓存工具
     */
    private final RedisCache redisCache;

    /**
     * 存储文件配置
     */
    private final StorageFileProperties storageFileProperties;

    /**
     * 构造存储文件短期访问授权服务
     *
     * @param redisCache Redis 缓存工具
     * @param storageFileProperties 存储文件配置
     */
    public StorageFileAccessTokenService(RedisCache redisCache, StorageFileProperties storageFileProperties)
    {
        this.redisCache = redisCache;
        this.storageFileProperties = storageFileProperties;
    }

    /**
     * 签发文件短期访问 token
     *
     * @param fileId 文件ID
     * @return 短期访问 token
     */
    public String issueToken(Long fileId)
    {
        return issueToken(fileId, null);
    }

    /**
     * 签发绑定当前登录用户的文件短期访问 token
     *
     * @param fileId 文件ID
     * @return 短期访问 token
     */
    public String issueUserBoundToken(Long fileId)
    {
        return issueToken(fileId, SecurityUtils.getUserId());
    }

    /**
     * 签发文件短期访问 token
     *
     * @param fileId 文件ID
     * @param userId 绑定用户ID，为空表示不绑定用户
     * @return 短期访问 token
     */
    private String issueToken(Long fileId, Long userId)
    {
        if (fileId == null)
        {
            return null;
        }
        String token = IdUtils.fastSimpleUUID();
        // 普通授权只绑定 fileId，高敏感授权会额外绑定当前登录用户ID
        redisCache.setCacheObject(getTokenKey(token), buildTokenValue(fileId, userId),
                storageFileProperties.getEffectiveAccessTokenExpireSeconds(), TimeUnit.SECONDS);
        return token;
    }

    /**
     * 校验文件短期访问 token
     *
     * @param fileId 文件ID
     * @param token 短期访问 token
     * @return true 表示 token 允许访问该文件
     */
    public boolean verifyToken(Long fileId, String token)
    {
        if (fileId == null || StringUtils.isBlank(token))
        {
            return false;
        }
        String tokenValue = redisCache.getCacheObject(getTokenKey(token));
        if (StringUtils.isBlank(tokenValue))
        {
            return false;
        }
        String[] values = tokenValue.split(":", -1);
        // token 必须和当前 fileId 精确匹配，避免拿一个文件的授权访问另一个文件
        if (values.length < 1 || !String.valueOf(fileId).equals(values[0]))
        {
            return false;
        }
        // 没有绑定用户ID的 token 属于普通短期授权，有效期内可直接访问
        if (values.length < 2 || StringUtils.isBlank(values[1]))
        {
            return true;
        }
        try
        {
            // 高敏感 token 必须同时校验当前登录用户，复制链接给其他账号也不能访问
            return values[1].equals(String.valueOf(SecurityUtils.getUserId()));
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * 给访问地址追加短期访问 token
     *
     * @param url 原始访问地址
     * @param token 短期访问 token
     * @return 追加 token 后的访问地址
     */
    public String appendToken(String url, String token)
    {
        if (StringUtils.isBlank(url) || StringUtils.isBlank(token))
        {
            return url;
        }
        // 已有查询参数时使用 & 追加，否则使用 ? 追加
        return url + (url.contains("?") ? "&" : "?") + "token=" + token;
    }

    /**
     * 构造 Redis token key
     *
     * @param token 短期访问 token
     * @return Redis key
     */
    private String getTokenKey(String token)
    {
        return CacheConstants.STORAGE_FILE_ACCESS_TOKEN_KEY + token;
    }

    /**
     * 构造 token 缓存值
     *
     * @param fileId 文件ID
     * @param userId 绑定用户ID
     * @return token 缓存值
     */
    private String buildTokenValue(Long fileId, Long userId)
    {
        // 使用 fileId:userId 保存授权范围，userId 为空时表示普通短期授权
        return String.valueOf(fileId) + ":" + (userId == null ? "" : String.valueOf(userId));
    }
}
