# DTO参数校验生成说明

## 改造目标

代码生成器在生成新增、编辑接口时，自动为 DTO 生成基础参数校验，减少每个业务模块重复手写 `@NotBlank`、`@NotNull` 和 `@Validated` 的成本。

## 生成规则

1. `SaveDto` 根据代码生成页面的“必填”配置生成校验注解。
2. `EditDto` 默认对主键字段生成 `@NotNull`，保证编辑接口必须带上业务主键。
3. `EditDto` 中勾选“必填”的编辑字段，也会生成对应校验注解。
4. `QueryDto` 默认不生成必填校验，避免列表查询条件被强约束。
5. 字符串字段使用 `@NotBlank(message = "xxx不能为空")`。
6. 非字符串字段使用 `@NotNull(message = "xxx不能为空")`。
7. Controller 的新增、编辑接口会生成 `@Validated @RequestBody`，由全局异常处理统一返回校验失败信息。

示例：

```java
@PostMapping
public Response<Void> add(@Validated @RequestBody DemoSaveDto demo)
{
    return Response.toResponse(demoService.insertDemo(demo));
}

@PutMapping
public Response<Void> edit(@Validated @RequestBody DemoEditDto demo)
{
    return Response.toResponse(demoService.updateDemo(demo));
}
```

生成后的 DTO 示例：

```java
public class DemoSaveDto extends BaseEntity
{
    @NotBlank(message = "名称不能为空")
    private String name;

    @NotNull(message = "排序不能为空")
    private Long sort;
}
```

```java
public class DemoEditDto extends BaseEntity
{
    @NotNull(message = "主键不能为空")
    private Long id;

    @NotBlank(message = "名称不能为空")
    private String name;
}
```

## 使用建议

DTO 校验只放通用、稳定、和接口入参强相关的规则，例如必填、长度、数字范围、格式等。

跨字段校验、状态流转校验、唯一性校验、权限相关校验、文件归属校验，建议继续放在 Service 层或领域服务里处理。这样 DTO 保持轻量，业务规则也不会被前端表单结构绑死。

当前编辑接口仍按若依常见的整表单提交方式生成，所以 `EditDto` 会按编辑表单的必填配置生成校验。如果某个业务以后需要真正的 PATCH 局部更新，可以单独新增 Patch DTO，避免和普通编辑接口混用。
