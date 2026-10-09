package com.ruoyi.framework.web.exception;

import java.util.Date;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.Response;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.BizException;
import com.ruoyi.common.exception.DemoModeException;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.ExceptionUtil;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.TraceIdUtils;
import com.ruoyi.common.utils.html.EscapeUtil;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.system.domain.SysErrorLog;
import com.ruoyi.system.service.ISysErrorLogService;

/**
 * 全局异常处理器
 *
 * @author ruoyi
 */
@RestControllerAdvice
public class GlobalExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 异常消息最大保存长度 */
    private static final int ERROR_MESSAGE_MAX_LENGTH = 2000;

    /** 异常堆栈最大保存长度，避免单条异常日志过大 */
    private static final int STACK_TRACE_MAX_LENGTH = 8000;

    @Autowired
    private ISysErrorLogService errorLogService;

    /**
     * 权限校验异常
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Response<Void> handleAccessDeniedException(AccessDeniedException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',权限校验失败'{}'", requestURI, e.getMessage());
        recordErrorLog(e, request);
        return Response.fail(HttpStatus.FORBIDDEN, "没有权限，请联系管理员授权");
    }

    /**
     * 请求方式不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Response<Void> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException e,
            HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',不支持'{}'请求", requestURI, e.getMethod());
        recordErrorLog(e, request);
        return Response.fail(e.getMessage());
    }

    /**
     * 业务异常
     */
    @ExceptionHandler(ServiceException.class)
    public Response<Void> handleServiceException(ServiceException e, HttpServletRequest request)
    {
        log.error(e.getMessage(), e);
        recordErrorLog(e, request);
        Integer code = e.getCode();
        return StringUtils.isNotNull(code) ? Response.fail(code, e.getMessage()) : Response.fail(e.getMessage());
    }

    /**
     * 自定义业务异常
     */
    @ExceptionHandler(BizException.class)
    public Response<Void> handleBizException(BizException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生业务异常'{}'", requestURI, e.getErrorMessage());
        recordErrorLog(e, request);
        return Response.fail(e);
    }

    /**
     * 请求路径中缺少必需的路径变量
     */
    @ExceptionHandler(MissingPathVariableException.class)
    public Response<Void> handleMissingPathVariableException(MissingPathVariableException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求路径中缺少必需的路径变量'{}',发生系统异常.", requestURI, e);
        recordErrorLog(e, request);
        return Response.fail(String.format("请求路径中缺少必需的路径变量[%s]", e.getVariableName()));
    }

    /**
     * 请求参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Response<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e,
            HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        String value = Convert.toStr(e.getValue());
        if (StringUtils.isNotEmpty(value))
        {
            value = EscapeUtil.clean(value);
        }
        log.error("请求参数类型不匹配'{}',发生系统异常.", requestURI, e);
        recordErrorLog(e, request);
        return Response.fail(String.format("请求参数类型不匹配，参数[%s]要求类型为：'%s'，但输入值为：'%s'", e.getName(),
                e.getRequiredType().getName(), value));
    }

    /**
     * 拦截未知的运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public Response<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生未知异常.", requestURI, e);
        recordErrorLog(e, request);
        return Response.fail(e.getMessage());
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public Response<Void> handleException(Exception e, HttpServletRequest request)
    {
        String requestURI = request.getRequestURI();
        log.error("请求地址'{}',发生系统异常.", requestURI, e);
        recordErrorLog(e, request);
        return Response.fail(e.getMessage());
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(BindException.class)
    public Response<Void> handleBindException(BindException e, HttpServletRequest request)
    {
        log.error(e.getMessage(), e);
        recordErrorLog(e, request);
        String message = e.getAllErrors().get(0).getDefaultMessage();
        return Response.fail(message);
    }

    /**
     * 自定义验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Response<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
            HttpServletRequest request)
    {
        log.error(e.getMessage(), e);
        recordErrorLog(e, request);
        String message = e.getBindingResult().getFieldError().getDefaultMessage();
        return Response.fail(message);
    }

    /**
     * 演示模式异常
     */
    @ExceptionHandler(DemoModeException.class)
    public Response<Void> handleDemoModeException(DemoModeException e)
    {
        return Response.fail("演示模式，不允许操作");
    }

    /**
     * 保存异常日志。日志保存失败不能影响接口原有异常响应。
     */
    private void recordErrorLog(Exception e, HttpServletRequest request)
    {
        try
        {
            SysErrorLog errorLog = new SysErrorLog();
            errorLog.setTraceId(TraceIdUtils.getTraceId());
            errorLog.setRequestUri(StringUtils.substring(request.getRequestURI(), 0, 255));
            errorLog.setRequestMethod(StringUtils.substring(request.getMethod(), 0, 20));
            errorLog.setExceptionName(StringUtils.substring(e.getClass().getName(), 0, 255));
            errorLog.setErrorMessage(StringUtils.substring(Convert.toStr(e.getMessage(),
                    ExceptionUtil.getRootErrorMessage(e)), 0, ERROR_MESSAGE_MAX_LENGTH));
            errorLog.setStackTrace(StringUtils.substring(ExceptionUtil.getExceptionMessage(e), 0, STACK_TRACE_MAX_LENGTH));
            errorLog.setIpaddr(IpUtils.getIpAddr());
            errorLog.setUserAgent(StringUtils.substring(request.getHeader("User-Agent"), 0, 500));
            errorLog.setErrorTime(new Date());
            setLoginUser(errorLog);
            errorLogService.insertErrorLog(errorLog);
        }
        catch (Exception logException)
        {
            log.warn("保存异常日志失败: {}", logException.getMessage());
        }
    }

    /**
     * 设置当前登录用户信息，匿名请求忽略即可。
     */
    private void setLoginUser(SysErrorLog errorLog)
    {
        try
        {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (loginUser != null)
            {
                errorLog.setUserId(loginUser.getUserId());
                errorLog.setUserName(loginUser.getUsername());
            }
        }
        catch (Exception ignored)
        {
        }
    }
}
