# API Controller 持久层访问边界设计

- 日期：2026-10-04
- 状态：待用户审阅
- 范围：当前未提交的模块化单体整理中的一项低风险分层修正

## 背景

后端代码已整理为 `api`、`modules`、`common` 和 `infrastructure` 等目录，但梗发布接口的 `MemeController` 仍直接注入并调用 `MemeTagMapper`，还在 Controller 中把 `MemeTag` 转成 API VO。`HomeController` 另有一条未使用的 `Meme` 导入。

只读检索还发现 67 个业务类共有 228 处引用 `api` 下的 DTO/VO。整体迁移这些应用契约会显著扩大本轮范围，因此暂列为后续工作。当前 `IMemeTagService` 没有其他业务消费者，可将其从通用 MyBatis `IService<MemeTag>` 表面收敛为明确的应用查询接口。

## 目标

1. 让 API Controller 不直接依赖 MyBatis Mapper 或业务实体。
2. 将标签读取和 `MemeTagVO` 组装放入梗模块应用服务。
3. 保持已有 REST 路径、响应结构和业务行为不变。
4. 用现有测试依赖增加应用服务映射测试和 Controller 分层约束测试。

## 设计

### 标签查询

- 将 `IMemeTagService` 收敛为显式查询契约，提供 `listTagOptions()`，返回 `List<MemeTagVO>`。
- `MemeTagServiceImpl` 在梗模块内部使用 `MemeTagMapper` 查询现有全部标签，并负责 `MemeTag` 到 `MemeTagVO` 的映射。
- 保持当前 `selectList(null)` 的查询语义，以及空结果返回空列表的行为。
- 不增加第三方依赖，不改变表结构或 SQL。

### API Controller

- `MemeController` 注入 `IMemeTagService` 并调用 `listTagOptions()`；移除对 `MemeTagMapper` 和 `MemeTag` 的直接依赖。
- `HomeController` 删除未使用的 `Meme` 导入。
- 其余 Controller、应用服务、DTO/VO 和模块间调用保持不变。

### 分层约束

- 新增 JUnit 架构测试，扫描用户端和管理端 Controller 源码，禁止导入 `modules.*.domain.entity` 和 `modules.*.infrastructure.persistence.mapper`。
- 延续现有 `CommonDependencyTests` 对 `common` 不反向依赖业务代码的约束。
- 不在本轮迁移业务模块对 `api` DTO/VO 的引用，也不重构业务模块之间已有的调用关系。

## 兼容性

- `GET /memes/tags` 路径、HTTP 方法和统一响应封装保持不变。
- 标签响应仍为 `Result<List<MemeTagVO>>`，`id`、`name` 字段及查询顺序语义保持不变。
- 不改变业务规则、认证与异常处理、数据库配置、`dev` profile 或其他接口。
- 保留当前工作区已有的全部未提交改动，不进行重置、格式化或无关清理。

## 验证

1. 为 `listTagOptions()` 增加单元测试，验证标签 ID/名称映射和空结果行为；使用已有 JUnit、Mockito 依赖。
2. 运行后端 `./mvnw.cmd test`，覆盖新增测试、现有请求支持测试、公共层依赖测试和 Spring 上下文加载。
3. 本轮不修改前端；现有用户端与管理端 lint、生产构建已通过，可沿用该结果。构建中的包体积警告不在本轮处理。
4. 本轮不修改 MemeAgent。当前环境没有 `pytest`，不安装依赖；Agent 测试维持未运行状态。

后端 Spring 上下文测试使用 `dev` 配置，并连接当前配置的 MySQL 与 Redis。运行前应确认这些服务可用；如果测试环境配置变化导致连接不可用，应报告具体失败，不为绕过问题改动业务配置。

## 后续事项

- 为 67 个业务类中的 228 处 API DTO/VO 依赖单独设计迁移方案，并保持对外 JSON 契约不变。
- 是否优化用户端约 800 KiB、管理端约 1.75 MiB 的生产入口包体积，另行决定。
