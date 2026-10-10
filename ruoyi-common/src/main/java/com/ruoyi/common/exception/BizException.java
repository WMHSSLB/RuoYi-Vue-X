package com.ruoyi.common.exception;

/**
 * 自定义业务异常。
 * <p>
 * 用于抛出可预期的业务失败，并携带稳定的错误码，方便全局异常处理器统一转换为接口响应。
 */
public class BizException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final String errorCode;

    /** 错误消息 */
    private final String errorMessage;

    /**
     * 根据错误码接口构造业务异常。
     *
     * @param exception 错误码接口
     */
    public BizException(BaseExceptionInterface exception)
    {
        super(exception.getErrorMessage());
        this.errorCode = exception.getErrorCode();
        this.errorMessage = exception.getErrorMessage();
    }

    /**
     * 根据错误码和错误消息构造业务异常。
     *
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     */
    public BizException(String errorCode, String errorMessage)
    {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * 获取错误码。
     *
     * @return 错误码
     */
    public String getErrorCode()
    {
        return errorCode;
    }

    /**
     * 获取错误消息。
     *
     * @return 错误消息
     */
    public String getErrorMessage()
    {
        return errorMessage;
    }
}
