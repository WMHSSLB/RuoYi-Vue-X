package com.ruoyi.common.exception;

/**
 * Business exception carrying a stable error code.
 */
public class BizException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final String errorCode;

    private final String errorMessage;

    public BizException(BaseExceptionInterface exception)
    {
        super(exception.getErrorMessage());
        this.errorCode = exception.getErrorCode();
        this.errorMessage = exception.getErrorMessage();
    }

    public BizException(String errorCode, String errorMessage)
    {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }
}
