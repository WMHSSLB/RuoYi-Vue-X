package com.ruoyi.common.core.domain;

import java.io.Serializable;
import com.ruoyi.common.exception.BaseExceptionInterface;
import com.ruoyi.common.exception.BizException;

/**
 * 统一接口响应体。
 *
 * @param <T> 响应数据类型
 */
public class Response<T> implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 默认成功消息 */
    private static final String SUCCESS_MESSAGE = "\u64CD\u4F5C\u6210\u529F";

    /** 默认失败消息 */
    private static final String FAIL_MESSAGE = "\u64CD\u4F5C\u5931\u8D25";

    /** 是否成功 */
    private boolean success = true;

    /** 响应消息 */
    private String message;

    /** 错误码 */
    private String errorCode;

    /** 响应数据 */
    private T data;

    /**
     * 返回默认成功响应。
     *
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    public static <T> Response<T> success()
    {
        Response<T> response = new Response<>();
        response.setMessage(SUCCESS_MESSAGE);
        return response;
    }

    /**
     * 返回带数据的成功响应。
     *
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    public static <T> Response<T> success(T data)
    {
        Response<T> response = success();
        response.setData(data);
        return response;
    }

    /**
     * 返回带自定义消息和数据的成功响应。
     *
     * @param message 响应消息
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功响应
     */
    public static <T> Response<T> success(String message, T data)
    {
        Response<T> response = success(data);
        response.setMessage(message);
        return response;
    }

    /**
     * 返回默认失败响应。
     *
     * @param <T> 响应数据类型
     * @return 失败响应
     */
    public static <T> Response<T> fail()
    {
        Response<T> response = new Response<>();
        response.setSuccess(false);
        response.setMessage(FAIL_MESSAGE);
        return response;
    }

    /**
     * 返回带错误消息的失败响应。
     *
     * @param errorMessage 错误消息
     * @param <T> 响应数据类型
     * @return 失败响应
     */
    public static <T> Response<T> fail(String errorMessage)
    {
        Response<T> response = fail();
        response.setMessage(errorMessage);
        return response;
    }

    /**
     * 返回带错误消息和数据的失败响应。
     *
     * @param errorMessage 错误消息
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 失败响应
     */
    public static <T> Response<T> fail(String errorMessage, T data)
    {
        Response<T> response = fail(errorMessage);
        response.setData(data);
        return response;
    }

    /**
     * 返回带错误码和错误消息的失败响应。
     *
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     * @param <T> 响应数据类型
     * @return 失败响应
     */
    public static <T> Response<T> fail(int errorCode, String errorMessage)
    {
        return fail(String.valueOf(errorCode), errorMessage);
    }

    /**
     * 返回带错误码和错误消息的失败响应。
     *
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     * @param <T> 响应数据类型
     * @return 失败响应
     */
    public static <T> Response<T> fail(String errorCode, String errorMessage)
    {
        Response<T> response = fail(errorMessage);
        response.setErrorCode(errorCode);
        return response;
    }

    /**
     * 返回带错误码、错误消息和数据的失败响应。
     *
     * @param errorCode 错误码
     * @param errorMessage 错误消息
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 失败响应
     */
    public static <T> Response<T> fail(String errorCode, String errorMessage, T data)
    {
        Response<T> response = fail(errorCode, errorMessage);
        response.setData(data);
        return response;
    }

    /**
     * 返回警告响应。
     *
     * @param message 警告消息
     * @param <T> 响应数据类型
     * @return 警告响应
     */
    public static <T> Response<T> warn(String message)
    {
        return fail("601", message);
    }

    /**
     * 返回带数据的警告响应。
     *
     * @param message 警告消息
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 警告响应
     */
    public static <T> Response<T> warn(String message, T data)
    {
        return fail("601", message, data);
    }

    /**
     * 将受影响行数转换为响应结果。
     *
     * @param rows 受影响行数
     * @return 响应结果
     */
    public static Response<Void> toResponse(int rows)
    {
        return rows > 0 ? success() : fail(FAIL_MESSAGE);
    }

    /**
     * 将布尔结果转换为响应结果。
     *
     * @param result 操作结果
     * @return 响应结果
     */
    public static Response<Void> toResponse(boolean result)
    {
        return result ? success() : fail(FAIL_MESSAGE);
    }

    /**
     * 根据业务异常返回失败响应。
     *
     * @param bizException 业务异常
     * @param <T> 响应数据类型
     * @return 失败响应
     */
    public static <T> Response<T> fail(BizException bizException)
    {
        return fail(bizException.getErrorCode(), bizException.getErrorMessage());
    }

    /**
     * 根据错误码接口返回失败响应。
     *
     * @param exception 错误码接口
     * @param <T> 响应数据类型
     * @return 失败响应
     */
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
