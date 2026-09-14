package com.ruoyi.common.enums;

import com.ruoyi.common.constant.StorageFileConstants;

/**
 * 存储文件访问级别
 * <p>
 * 数据库 access 字段保存 code，代码中通过枚举表达公开和私有的访问语义
 *
 * @author 955Kane
 */
public enum StorageFileAccess
{
    /**
     * 公开访问文件
     */
    PUBLIC(StorageFileConstants.ACCESS_PUBLIC),

    /**
     * 私有访问文件
     */
    PRIVATE(StorageFileConstants.ACCESS_PRIVATE);

    /**
     * 数据库存储值
     */
    private final String code;

    StorageFileAccess(String code)
    {
        this.code = code;
    }

    public String getCode()
    {
        return code;
    }

    /**
     * 判断访问级别是否为公开
     *
     * @param code 数据库存储访问级别
     * @return true 表示公开文件
     */
    public static boolean isPublic(String code)
    {
        return PUBLIC.code.equals(code);
    }

    /**
     * 判断访问级别是否为私有
     *
     * @param code 数据库存储访问级别
     * @return true 表示私有文件
     */
    public static boolean isPrivate(String code)
    {
        return PRIVATE.code.equals(code);
    }
}
