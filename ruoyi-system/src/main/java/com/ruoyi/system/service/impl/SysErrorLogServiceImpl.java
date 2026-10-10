package com.ruoyi.system.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.SysErrorLog;
import com.ruoyi.system.mapper.SysErrorLogMapper;
import com.ruoyi.system.service.ISysErrorLogService;

/**
 * 系统异常日志 服务层处理
 *
 * @author 955Kane
 */
@Service
public class SysErrorLogServiceImpl implements ISysErrorLogService
{
    @Autowired
    private SysErrorLogMapper errorLogMapper;

    /**
     * 新增系统异常日志
     *
     * @param errorLog 异常日志对象
     */
    @Override
    public void insertErrorLog(SysErrorLog errorLog)
    {
        errorLogMapper.insertErrorLog(errorLog);
    }

    /**
     * 查询系统异常日志集合
     *
     * @param errorLog 异常日志查询条件
     * @return 异常日志集合
     */
    @Override
    public List<SysErrorLog> selectErrorLogList(SysErrorLog errorLog)
    {
        return errorLogMapper.selectErrorLogList(errorLog);
    }

    /**
     * 查询系统异常日志详情
     *
     * @param errorId 异常日志ID
     * @return 异常日志对象
     */
    @Override
    public SysErrorLog selectErrorLogById(Long errorId)
    {
        return errorLogMapper.selectErrorLogById(errorId);
    }

    /**
     * 批量删除系统异常日志
     *
     * @param errorIds 需要删除的异常日志ID
     * @return 结果
     */
    @Override
    public int deleteErrorLogByIds(Long[] errorIds)
    {
        return errorLogMapper.deleteErrorLogByIds(errorIds);
    }

    /**
     * 清空系统异常日志
     */
    @Override
    public void cleanErrorLog()
    {
        errorLogMapper.cleanErrorLog();
    }
}
