package com.ruoyi.framework.config;

import java.util.Date;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;

/**
 * MyBatis-Plus 公共字段自动填充处理器。
 * <p>
 * 仅对使用 MyBatis-Plus insert/update API 的实体生效，XML 自定义 SQL 仍按显式赋值逻辑处理。
 *
 * @author ruoyi
 */
@Component
public class MyBatisPlusMetaObjectHandler implements MetaObjectHandler
{
    private static final String SYSTEM_OPERATOR = "system";

    @Override
    public void insertFill(MetaObject metaObject)
    {
        Date now = DateUtils.getNowDate();
        String username = getUsernameQuietly();
        this.strictInsertFill(metaObject, "createBy", String.class, username);
        this.strictInsertFill(metaObject, "createTime", Date.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject)
    {
        Date now = DateUtils.getNowDate();
        String username = getUsernameQuietly();
        this.strictUpdateFill(metaObject, "updateBy", String.class, username);
        this.strictUpdateFill(metaObject, "updateTime", Date.class, now);
    }

    /**
     * 兼容定时任务、启动初始化等没有登录上下文的场景。
     */
    private String getUsernameQuietly()
    {
        try
        {
            String username = SecurityUtils.getUsername();
            return StringUtils.isBlank(username) ? SYSTEM_OPERATOR : username;
        }
        catch (Exception e)
        {
            return SYSTEM_OPERATOR;
        }
    }
}
