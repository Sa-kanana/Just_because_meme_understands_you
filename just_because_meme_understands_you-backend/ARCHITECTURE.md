# 后端模块结构与依赖约定

项目采用按业务划分的模块化单体结构。目前仍是一个 Spring Boot 应用、一个 Maven 构建产物，沿用现有数据库、事务和 dev 配置，不新增微服务相关依赖。

## 目录结构

```text
com.sakana.just_because_meme_understands_you
├── api/                         HTTP 接入层
│   ├── admin/                   管理端 controller、dto、vo、support
│   ├── user/                    用户端 controller、dto、vo、support
│   └── shared/exception/        全局异常处理
├── modules/                     按业务能力划分
│   ├── admin/                   管理用例
│   ├── ai/                      AI 会话、搜索与知识库
│   ├── auth/                    登录、验证码、会话与认证配置
│   ├── comment/                 评论、评论图片与评论点赞
│   ├── feedback/                反馈
│   ├── help/                    帮助内容
│   ├── home/                    首页与轮播图
│   ├── meme/                    梗内容、资源、标签与浏览统计
│   ├── moderation/              敏感词过滤与词库刷新
│   ├── notification/            用户通知
│   ├── oss/                     对象存储与上传
│   └── user/                    账户、资料、收藏、关注、点赞与活动
├── common/                      可复用公共能力
│   ├── data/                    通用数据状态
│   ├── exception/               通用异常类型
│   ├── pagination/              分页模型与参数归一化
│   ├── ratelimit/               限流注解、切面与 Redis 计数
│   ├── response/                统一响应封装
│   ├── security/                当前请求身份上下文
│   ├── transaction/             事务提交后执行工具
│   ├── validation/              通用标识参数校验
│   └── util/                    IP、摘要、邮箱等工具
└── infrastructure/              应用级技术配置与通用请求过滤器
```

业务模块按需要包含以下层级，不创建没有代码的空层：

```text
modules/<业务模块>/
├── application/                 服务接口、impl、用例、assembler、support
├── domain/                      业务常量、类型与 entity
└── infrastructure/
    ├── client/                  外部服务客户端及报文
    ├── config/                  专属配置、拦截器与监听器
    ├── persistence/mapper/      MyBatis 数据访问接口
    ├── persistence/projection/  数据库查询投影
    └── task/                    定时任务
```

用户模块的应用层进一步按 account、profile、favorite、follow、like、activity 分包。收藏夹的 usecase、assembler、support 位于 favorite 内。

## 公共层复用边界

- common 只依赖 JDK、已有第三方库和自身公共类型，不引用 api、modules 或项目 infrastructure。CommonDependencyTests 检查这一源码依赖约束。
- 限流通过 @RateLimit 声明规则；分页通过 PageVO<T> 复用；通用标识校验接收字段名称；事务提交后操作接收回调。
- 认证常量、密码策略归 auth；通知常量归 notification；资源类型归 meme；用户状态归 user。
- AuthContext 仅提供当前请求身份。管理员鉴权归 api/admin/support，收藏夹特殊编号规则归 api/user/support。
- BizException 属于公共层；将异常转换为 HTTP 响应的 GlobalExceptionHandler 属于 api/shared/exception。

## 装配与兼容性

启动类扫描根包下的 Spring 组件。MyBatis 仅扫描 modules 下带 @Mapper 的接口，避免将普通业务接口注册为 Mapper。XML 仍位于 src/main/resources/mapper，其 namespace 和类型名称已同步更新。

本次整理保留接口路径、字段、业务规则、SQL 和模块间原有调用。业务模块仍有直接服务、实体和 Mapper 引用，应用服务沿用现有 DTO/VO，因此当前尚不支持独立部署。真正拆分服务时，需要另行定义模块契约、隔离持久化访问并处理跨服务事务。

在后端目录执行 `./mvnw.cmd clean test`，验证公共层依赖约束、请求参数兼容性和 Spring 上下文装配。
