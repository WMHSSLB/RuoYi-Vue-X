package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 系统异常日志 sys_error_log
 *
 * @author ruoyi
 */
public class SysErrorLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 异常日志ID */
    private Long errorId;

    /** 链路追踪ID */
    @Excel(name = "链路追踪ID")
    private String traceId;

    /** 用户ID */
    @Excel(name = "用户ID")
    private Long userId;

    /** 用户账号 */
    @Excel(name = "用户账号")
    private String userName;

    /** 请求地址 */
    @Excel(name = "请求地址")
    private String requestUri;

    /** 请求方式 */
    @Excel(name = "请求方式")
    private String requestMethod;

    /** 异常类名 */
    @Excel(name = "异常类名")
    private String exceptionName;

    /** 异常消息 */
    @Excel(name = "异常消息")
    private String errorMessage;

    /** 异常堆栈 */
    private String stackTrace;

    /** 业务类型 */
    @Excel(name = "业务类型")
    private String bizType;

    /** 业务ID */
    @Excel(name = "业务ID")
    private String bizId;

    /** IP地址 */
    @Excel(name = "IP地址")
    private String ipaddr;

    /** 用户代理 */
    private String userAgent;

    /** 发生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "发生时间", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date errorTime;

    public Long getErrorId()
    {
        return errorId;
    }

    public void setErrorId(Long errorId)
    {
        this.errorId = errorId;
    }

    public String getTraceId()
    {
        return traceId;
    }

    public void setTraceId(String traceId)
    {
        this.traceId = traceId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public String getUserName()
    {
        return userName;
    }

    public void setUserName(String userName)
    {
        this.userName = userName;
    }

    public String getRequestUri()
    {
        return requestUri;
    }

    public void setRequestUri(String requestUri)
    {
        this.requestUri = requestUri;
    }

    public String getRequestMethod()
    {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod)
    {
        this.requestMethod = requestMethod;
    }

    public String getExceptionName()
    {
        return exceptionName;
    }

    public void setExceptionName(String exceptionName)
    {
        this.exceptionName = exceptionName;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    public String getStackTrace()
    {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace)
    {
        this.stackTrace = stackTrace;
    }

    public String getBizType()
    {
        return bizType;
    }

    public void setBizType(String bizType)
    {
        this.bizType = bizType;
    }

    public String getBizId()
    {
        return bizId;
    }

    public void setBizId(String bizId)
    {
        this.bizId = bizId;
    }

    public String getIpaddr()
    {
        return ipaddr;
    }

    public void setIpaddr(String ipaddr)
    {
        this.ipaddr = ipaddr;
    }

    public String getUserAgent()
    {
        return userAgent;
    }

    public void setUserAgent(String userAgent)
    {
        this.userAgent = userAgent;
    }

    public Date getErrorTime()
    {
        return errorTime;
    }

    public void setErrorTime(Date errorTime)
    {
        this.errorTime = errorTime;
    }
}
