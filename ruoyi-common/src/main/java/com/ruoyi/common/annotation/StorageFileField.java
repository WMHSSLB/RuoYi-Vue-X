package com.ruoyi.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务文件字段标记
 *
 * @author ruoyi
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface StorageFileField
{
    /**
     * 文件业务类型
     */
    String bizType();

    /**
     * 文件回显列表字段名，默认使用当前字段名 + List
     */
    String listField() default "";
}
