package com.ruoyi.common.enums;

/**
 * 存储文件状态
 * <p>
 * 数据库 file_status 字段保存 code，代码中通过枚举表达状态语义
 *
 * @author 955Kane
 */
public enum StorageFileStatus
{
    /**
     * 临时文件
     */
    TEMP("0"),

    /**
     * 已使用文件
     */
    USED("1"),

    /**
     * 待删除文件
     */
    WAIT_DELETE("2"),

    /**
     * 已删除文件
     */
    DELETED("3");

    /**
     * 数据库存储值
     */
    private final String code;

    StorageFileStatus(String code)
    {
        this.code = code;
    }

    public String getCode()
    {
        return code;
    }

    /**
     * 判断状态是否表示文件已失效
     *
     * @param code 数据库存储状态值
     * @return true 表示文件不可继续访问
     */
    public static boolean isInvalid(String code)
    {
        return WAIT_DELETE.code.equals(code) || DELETED.code.equals(code);
    }
}
