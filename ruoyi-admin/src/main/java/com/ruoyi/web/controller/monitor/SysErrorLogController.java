package com.ruoyi.web.controller.monitor;

import java.util.List;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.system.domain.SysErrorLog;
import com.ruoyi.system.service.ISysErrorLogService;

/**
 * 异常日志记录
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/monitor/errorlog")
public class SysErrorLogController extends BaseController
{
    @Autowired
    private ISysErrorLogService errorLogService;

    @PreAuthorize("@ss.hasPermi('monitor:errorlog:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysErrorLog errorLog)
    {
        startPage();
        List<SysErrorLog> list = errorLogService.selectErrorLogList(errorLog);
        return getDataTable(list);
    }

    @PreAuthorize("@ss.hasPermi('monitor:errorlog:query')")
    @GetMapping(value = "/{errorId}")
    public AjaxResult getInfo(@PathVariable Long errorId)
    {
        return success(errorLogService.selectErrorLogById(errorId));
    }

    @Log(title = "异常日志", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('monitor:errorlog:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysErrorLog errorLog)
    {
        List<SysErrorLog> list = errorLogService.selectErrorLogList(errorLog);
        ExcelUtil<SysErrorLog> util = new ExcelUtil<SysErrorLog>(SysErrorLog.class);
        util.exportExcel(response, list, "异常日志");
    }

    @Log(title = "异常日志", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('monitor:errorlog:remove')")
    @DeleteMapping("/{errorIds}")
    public AjaxResult remove(@PathVariable Long[] errorIds)
    {
        return toAjax(errorLogService.deleteErrorLogByIds(errorIds));
    }

    @Log(title = "异常日志", businessType = BusinessType.CLEAN)
    @PreAuthorize("@ss.hasPermi('monitor:errorlog:remove')")
    @DeleteMapping("/clean")
    public AjaxResult clean()
    {
        errorLogService.cleanErrorLog();
        return success();
    }
}
