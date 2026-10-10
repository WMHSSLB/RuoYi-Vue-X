package com.ruoyi.system.mapper;

import java.util.List;
import com.ruoyi.system.domain.SysErrorLog;

/**
 * 系统异常日志 数据层
 *
 * @author 955Kane
 */
public interface SysErrorLogMapper
{
    /**
     * 新增系统异常日志
     *
     * @param errorLog 异常日志对象
     */
    public void insertErrorLog(SysErrorLog errorLog);

    /**
     * 查询系统异常日志集合
     *
     * @param errorLog 异常日志查询条件
     * @return 异常日志集合
     */
    public List<SysErrorLog> selectErrorLogList(SysErrorLog errorLog);

    /**
     * 查询系统异常日志详情
     *
     * @param errorId 异常日志ID
     * @return 异常日志对象
     */
    public SysErrorLog selectErrorLogById(Long errorId);

    /**
     * 批量删除系统异常日志
     *
     * @param errorIds 需要删除的异常日志ID
     * @return 结果
     */
    public int deleteErrorLogByIds(Long[] errorIds);

    /**
     * 清空系统异常日志
     */
    public void cleanErrorLog();
}
