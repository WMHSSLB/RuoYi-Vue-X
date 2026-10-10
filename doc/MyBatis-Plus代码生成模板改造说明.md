# MyBatis-Plus 代码生成模板改造说明

## 1. 改造目标

若依原生代码生成模板会生成普通 `Mapper`、`Service` 和 `ServiceImpl`，即使实体类已经增加了 MyBatis-Plus 的 `@TableName`、`@Version` 等注解，生成后的业务层仍不能直接使用 MyBatis-Plus 的通用 CRUD 能力。

本次改造目标：

- `Mapper` 继承 `BaseMapper<T>`
- `Service` 继承 `IService<T>`
- `ServiceImpl` 继承 `ServiceImpl<Mapper, T>`
- 保留若依原有 XML 自定义方法
- 实体类生成 `@TableName("数据库原始表名")`
- `@TableName` 不参与代码生成去前缀逻辑，避免生成器类名规则影响 MP 运行时映射

## 2. 生成链路

```mermaid
flowchart TD
    A["导入业务表"] --> B["GenUtils 初始化字段信息"]
    B --> C["VelocityUtils 准备模板变量"]
    C --> D["mapper.java.vm"]
    C --> E["service.java.vm"]
    C --> F["serviceImpl.java.vm"]
    C --> G["domain.java.vm / sub-domain.java.vm"]
    D --> H["XxxMapper extends BaseMapper<Xxx>"]
    E --> I["IXxxService extends IService<Xxx>"]
    F --> J["XxxServiceImpl extends ServiceImpl<XxxMapper, Xxx>"]
    G --> K["@TableName(\"数据库原始表名\")"]
    K --> L["MP 通用方法直接使用真实物理表名"]
```

## 3. Mapper 模板

文件：

```text
ruoyi-generator/src/main/resources/vm/java/mapper.java.vm
```

生成结果：

```java
public interface NodeMapper extends BaseMapper<Node>
```

说明：

- `BaseMapper<Node>` 提供 MP 通用 CRUD 方法
- 若依原有 `selectNodeList`、`insertNode` 等 XML 方法继续保留
- Controller 和 Service 原有调用链不需要被迫改成 MP 写法

## 4. Service 模板

文件：

```text
ruoyi-generator/src/main/resources/vm/java/service.java.vm
```

生成结果：

```java
public interface INodeService extends IService<Node>
```

说明：

- `IService<Node>` 提供 MP 通用 Service 方法
- 若依生成的业务方法继续保留，兼容现有 Controller 调用方式

## 5. ServiceImpl 模板

文件：

```text
ruoyi-generator/src/main/resources/vm/java/serviceImpl.java.vm
```

生成结果：

```java
public class NodeServiceImpl extends ServiceImpl<NodeMapper, Node> implements INodeService
```

说明：

- `ServiceImpl<NodeMapper, Node>` 让实现类接入 MP 通用 Service 能力
- 原有 `nodeMapper.selectNodeList(node)` 这类 XML 方法继续可用

## 6. 实体表名注解

文件：

```text
ruoyi-generator/src/main/resources/vm/java/domain.java.vm
ruoyi-generator/src/main/resources/vm/java/sub-domain.java.vm
```

生成结果示例：

```java
@TableName("tb_node")
public class Node extends BaseEntity
```

如果真实表名是：

```text
tb_node
```

实体注解也直接写：

```text
tb_node
```

说明：

- 代码生成器去前缀只用于类名、业务名等代码命名
- `@TableName` 使用数据库原始表名
- XML 自定义 SQL 与 MP 通用方法都指向同一张真实表

## 7. VelocityUtils 模板变量

文件：

```text
ruoyi-generator/src/main/java/com/ruoyi/generator/util/VelocityUtils.java
```

当前模板变量在若依原有实体、Mapper、Service、Controller、XML 和前端模板生成链路基础上，额外提供 `dtoImportList` 和 `voImportList`：

- `dto.java.vm` 生成 `domain` 同级 `dto` 包下的 `${ClassName}Dto`
- `vo.java.vm` 生成 `domain` 同级 `vo` 包下的 `${ClassName}Vo`
- DTO/VO 继承 `BaseEntity`，类上生成 `@Schema`、`@Data`、`@EqualsAndHashCode(callSuper = false)`
- DTO/VO 只保留接口对象需要的日期和数值导入，不生成 `@TableName`、`@Version` 等实体持久化注解
- `common.ts.vm` 不参与生成，前端 TypeScript 仍按现有 `api.ts.vm`、`type.ts.vm`、`index.ts.vm` 链路生成

## 8. 注意事项

- `@TableName` 应使用数据库原始表名，例如 `tb_node`
- 代码生成器表前缀配置只影响 Java 类名，不影响 `@TableName`
- XML 自定义 SQL 仍然直接使用真实表名，与 `@TableName` 保持一致
- 已经生成过的业务代码不会自动变化，需要重新生成或手动同步 Mapper、Service、ServiceImpl 和实体注解
