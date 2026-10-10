# MyBatis-Plus 自动填充说明

## 目标

项目保留若依原有 XML 代码生成风格，同时为后续使用 MyBatis-Plus API 的业务补齐公共审计字段自动填充能力。

## 生效范围

自动填充只对 MyBatis-Plus 的插入、更新 API 生效，例如：

```java
save(entity);
updateById(entity);
baseMapper.insert(entity);
baseMapper.updateById(entity);
```

若依代码生成器当前仍然生成 XML 自定义 SQL：

```java
xxxMapper.insertXxx(entity);
xxxMapper.updateXxx(entity);
```

这类 XML 方法不依赖 MP 自动填充，仍按 Service 中显式设置的字段值写入数据库。

## 字段配置

`BaseEntity` 中公共审计字段增加 MP 填充标记：

```java
@TableField(fill = FieldFill.INSERT)
private String createBy;

@TableField(fill = FieldFill.INSERT)
private Date createTime;

@TableField(fill = FieldFill.UPDATE)
private String updateBy;

@TableField(fill = FieldFill.UPDATE)
private Date updateTime;
```

同时 `searchValue`、`params` 是请求辅助字段，不是数据库字段，因此标记为：

```java
@TableField(exist = false)
```

避免后续使用 MP API 时误生成 `search_value`、`params` 等不存在的数据库列。

## 填充处理器

`MyBatisPlusMetaObjectHandler` 放在 `ruoyi-framework` 模块，统一填充：

- 新增：`createBy`、`createTime`
- 修改：`updateBy`、`updateTime`

当前登录人来自 `SecurityUtils.getUsername()`。如果定时任务、启动初始化等场景没有登录上下文，则使用 `system` 作为兜底操作人。

## 使用建议

- 若依原有系统模块保持原样，不强行改造。
- XML 生成代码继续保持当前模板，`createTime/updateTime` 仍由 Service 显式设置。
- 后续手写 MP 业务可以直接使用 `save/updateById`，公共审计字段由 `MetaObjectHandler` 自动填充。
- 如果业务需要清空某个字段为 `null`，不要依赖普通 `updateById`，应使用 `UpdateWrapper.set("field", null)` 或单独配置字段更新策略。
