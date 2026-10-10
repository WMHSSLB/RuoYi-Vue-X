package com.ruoyi.common.exception;

/**
 * 业务异常错误码约定。
 * <p>
 * 枚举或自定义异常类型实现该接口后，可以统一提供稳定的错误码和错误消息。
 */
public interface BaseExceptionInterface
{
    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    String getErrorCode();

    /**
     * 获取错误消息。
     *
     * @return 错误消息
     */
    String getErrorMessage();
}
