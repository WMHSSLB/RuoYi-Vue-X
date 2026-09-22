package com.ruoyi.common.core.domain;

import java.io.Serializable;
import com.ruoyi.common.exception.BaseExceptionInterface;
import com.ruoyi.common.exception.BizException;

/**
 * Unified API response body.
 *
 * @param <T> response data type
 */
public class Response<T> implements Serializable
{
    private static final long serialVersionUID = 1L;

    private static final String SUCCESS_MESSAGE = "\u64CD\u4F5C\u6210\u529F";

    private static final String FAIL_MESSAGE = "\u64CD\u4F5C\u5931\u8D25";

    private boolean success = true;

    private String message;

    private String errorCode;

    private T data;

    public static <T> Response<T> success()
    {
        Response<T> response = new Response<>();
        response.setMessage(SUCCESS_MESSAGE);
        return response;
    }

    public static <T> Response<T> success(T data)
    {
        Response<T> response = success();
        response.setData(data);
        return response;
    }

    public static <T> Response<T> success(String message, T data)
    {
        Response<T> response = success(data);
        response.setMessage(message);
        return response;
    }

    public static <T> Response<T> fail()
    {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        response.setMessage(FAIL_MESSAGE);
        return response;
    }

    public static <T> Response<T> fail(String errorMessage)
    {
        Response<T> response = fail();
        response.setMessage(errorMessage);
        return response;
    }

    public static <T> Response<T> fail(String errorMessage, T data)
    {
        Response<T> response = fail(errorMessage);
        response.setData(data);
        return response;
    }

    public static <T> Response<T> fail(int errorCode, String errorMessage)
    {
        return fail(String.valueOf(errorCode), errorMessage);
    }

    public static <T> Response<T> fail(String errorCode, String errorMessage)
    {
        Response<T> response = fail(errorMessage);
        response.setErrorCode(errorCode);
        return response;
    }

    public static <T> Response<T> fail(String errorCode, String errorMessage, T data)
    {
        Response<T> response = fail(errorCode, errorMessage);
        response.setData(data);
        return response;
    }

    public static <T> Response<T> warn(String message)
    {
        return fail("601", message);
    }

    public static <T> Response<T> warn(String message, T data)
    {
        return fail("601", message, data);
    }

    public static Response<Void> toResponse(int rows)
    {
        return rows > 0 ? success() : fail(FAIL_MESSAGE);
    }

    public static Response<Void> toResponse(boolean result)
    {
        return result ? success() : fail(FAIL_MESSAGE);
    }

    public static <T> Response<T> fail(BizException bizException)
    {
        return fail(bizException.getErrorCode(), bizException.getErrorMessage());
    }

    public static <T> Response<T> fail(BaseExceptionInterface exception)
    {
        return fail(exception.getErrorCode(), exception.getErrorMessage());
    }

    public boolean isSuccess()
    {
        return success;
    }

    public void setSuccess(boolean success)
    {
        this.success = success;
    }

    public String getMessage()
    {
        return message;
    }

    public void setMessage(String message)
    {
        this.message = message;
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public void setErrorCode(String errorCode)
    {
        this.errorCode = errorCode;
    }

    public T getData()
    {
        return data;
    }

    public void setData(T data)
    {
        this.data = data;
    }
}
