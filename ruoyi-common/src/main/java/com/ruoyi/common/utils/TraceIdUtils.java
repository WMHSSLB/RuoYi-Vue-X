package com.ruoyi.common.utils;

import java.util.UUID;
import org.slf4j.MDC;

/**
 * 链路ID工具类。
 *
 * @author ruoyi
 */
public class TraceIdUtils
{
    public static final String TRACE_ID = "traceId";

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    private TraceIdUtils()
    {
    }

    public static String createTraceId()
    {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String getTraceId()
    {
        return MDC.get(TRACE_ID);
    }

    public static void putTraceId(String traceId)
    {
        if (StringUtils.isNotBlank(traceId))
        {
            MDC.put(TRACE_ID, traceId);
        }
    }

    public static void clear()
    {
        MDC.remove(TRACE_ID);
    }
}
