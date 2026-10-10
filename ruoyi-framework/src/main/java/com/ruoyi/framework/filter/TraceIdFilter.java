package com.ruoyi.framework.filter;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.TraceIdUtils;

/**
 * 为每次请求生成链路ID，并写入MDC和响应头。
 *
 * @author 955Kane
 */
public class TraceIdFilter extends OncePerRequestFilter
{
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException
    {
        String traceId = request.getHeader(TraceIdUtils.TRACE_ID_HEADER);
        if (StringUtils.isBlank(traceId))
        {
            traceId = TraceIdUtils.createTraceId();
        }
        TraceIdUtils.putTraceId(traceId);
        response.setHeader(TraceIdUtils.TRACE_ID_HEADER, traceId);
        try
        {
            filterChain.doFilter(request, response);
        }
        finally
        {
            TraceIdUtils.clear();
        }
    }
}
