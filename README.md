# Just Because Meme Understands You

一个网络梗与梗图社区。用户可以浏览和搜索梗图，发布内容，点赞、评论、关注和收藏；登录后还可以使用 AI 搜梗，按自然语言查找相关内容。

仓库由四个可以独立启动的模块组成：用户端、管理端、Java 业务服务和 Python AI 服务。用户端与管理端都只访问 Java 服务，Java 服务再按需访问 MySQL、Redis、OSS 和 MemeAgent。

## 功能

- **梗社区**：首页推荐与热门标签、关键词搜索、梗详情、发布、点赞、评论与回复。
- **用户中心**：注册登录、GitHub OAuth、个人主页、关注、收藏夹、通知与账号设置。
- **AI 搜梗**：向量检索、SSE 流式回答、历史会话、知识库入库。
- **管理后台**：用户、梗内容、标签、轮播图、敏感词、知识库与 AI 运维管理。
- **热梗采集**：Java 调度 MemeAgent，通过 Firecrawl 采集并处理内容，可按需启用。

## 整体架构

| 模块 | 使用的工具 | 主要职责 |
| --- | --- | --- |
| 用户前端 | Vue 3、Vue Router 4、Pinia、Axios、Vuesax Alpha、vue-advanced-cropper、Vue CLI 5 | 首页、搜索、详情、发布、互动、用户中心、AI 页面 |
| 管理前端 | Vue 3、Vue Router 4、Pinia、Axios、Arco Design Vue、Vue CLI 5 | 用户、梗、标签、轮播图、敏感词、知识库和 AI 运维 |
| 业务后端 | Java 17、Spring Boot 3.5、Spring MVC、Spring WebFlux、Spring Security、MyBatis-Plus、Maven | 登录、权限、业务 API、文件上传、会话记录、AI 请求转发 |
| AI 服务 | Python 3.11、FastAPI、Uvicorn、Pydantic Settings、LangChain、LangChain OpenAI | SSE 搜索、Embedding、向量检索、异步灌库、知识库和热梗采集 |
| 业务数据 | MySQL、MyBatis-Plus | 用户、梗、评论、收藏、通知和 AI 会话 |
| 缓存与并发 | Redis、Redisson | 缓存、限流、分布式锁、计数同步和消息发布 |
| 向量数据 | PostgreSQL、pgvector、asyncpg | 梗和知识库文本的 Embedding 与相似度检索 |
| 文件与外部服务 | 阿里云 OSS、SMTP、GitHub OAuth、OpenAI 兼容模型 API、Firecrawl（可选） | 图片存储、邮件验证码、第三方登录、模型调用和采集 |

```text
用户前端 / 管理前端
        │ /api（开发代理会移除此前缀）
        ▼
Java 业务后端 ── MySQL / Redis / OSS
        │ 内部 API + SSE
        ▼
MemeAgent ── PostgreSQL + pgvector
        ├── 模型 API（对话与 Embedding）
        └── Firecrawl（可选）
```

业务数据和聊天记录由 Java + MySQL 管理。MemeAgent 不保存聊天会话，只负责生成回答和维护向量数据；Java 通过内部 API 调用它，并用 `X-Internal-Api-Key` 做服务间鉴权。

### 前端架构

两个前端都是 Vue CLI 单页应用。页面在 `src/views`，可复用组件在 `src/components`，路由在 `src/router`，Pinia 状态在 `src/stores`，后端请求集中在 `src/api`。

用户端开发服务器默认监听 80 端口，管理端监听 5174 端口。两个 `vue.config.js` 都把 `/api` 代理到 `http://localhost:8080`，并移除 `/api` 前缀，所以浏览器访问 `/api/list` 时，Java 实际收到的是 `/list`。请求代码读取 `VUE_APP_API_BASE_URL`，未设置时使用 `/api`。

用户端和管理端都使用 `VUE_APP_API_TIMEOUT` 设置 Axios 超时，默认值为 15000 毫秒。管理端仓库已经提供 `.env.development`，内容为 `/api` 和 15000 毫秒；用户端没有同名开发环境文件，直接使用代码默认值即可。生产构建使用 `.env.production.local`，变量名仍然是 `VUE_APP_API_BASE_URL`，不是 `VUE_APP_API_BASE`。

用户端使用 Vuesax Alpha 提供基础界面组件，使用 `vue-advanced-cropper` 处理图片裁剪；管理端使用 Arco Design Vue。Axios 统一处理 Cookie、JWT 刷新和接口错误，Pinia 保存登录状态。

### Java 后端架构

后端按 Controller、Service、Mapper、Entity/DTO/VO 分层。Controller 暴露 REST API，Service 处理业务规则和事务，Mapper 使用 MyBatis-Plus 访问 MySQL。`WebMvcConfig` 注册 JWT 和可选登录拦截器，`SecurityConfig`、限流组件和敏感词过滤器负责访问控制与内容安全。

后端还包含几条独立链路：

- Spring WebFlux `WebClient` 调用 MemeAgent，并把 AI 回答以 SSE 转给浏览器。
- Redis 和 Redisson 用于登录、AI、反馈、浏览、关注等接口的限流，以及点赞、评论、收藏计数同步。
- 阿里云 OSS SDK 生成上传策略，浏览器直传图片，后端只保存资源信息。
- Spring Mail 发送验证码和找回密码邮件；JJWT 处理访问令牌和刷新令牌；GitHub OAuth 使用 WebClient 完成登录回调。
- 定时任务负责热梗采集，采集开关、时间和发布账号在 `meme.crawl` 中配置。

### MemeAgent 架构

`MemeAgent/app` 是无状态 FastAPI 服务：

- `/stream` 接收 Java 转发的搜索请求，调用 LangChain Agent、Embedding 和模型 API，返回 SSE 事件。
- `/ingest`、`/ingest/delete` 处理梗向量的新增、更新和删除。
- `/ingest/knowledge` 处理管理后台上传的知识文档。
- `/crawl/hot-memes` 在开启 Firecrawl 时采集并整理热梗。
- 服务启动时通过 `asyncpg` 连接 PostgreSQL，并自动创建 `meme_vector_chunk`、`knowledge_vector_chunk` 表。

MemeAgent 的 `.env` 位于 `MemeAgent/.env`，不会读取根目录 `.env`。Java 的 `meme.agent.base-url`、`meme.agent.api-key` 必须和 Agent 的地址、`INTERNAL_API_KEY` 对应。

### 主要接口入口

| 调用方 | 路径 | 用途 |
| --- | --- | --- |
| 用户端 | `GET /list`、`GET /search`、`GET /detail` | 首页列表、关键词搜索、梗详情 |
| 用户端 | `/login`、`/register`、`/user/**` | 登录注册、个人资料、关注和收藏 |
| 用户端 | `/ai/**` | AI 搜索、会话和流式回答 |
| 管理端 | `/admin/users`、`/admin/memes`、`/admin/knowledge` | 管理用户、梗和知识库 |
| 管理端 | `/ai/ops`、`/ai/ingest`、`/ai/crawl` | AI 状态、向量回填和热梗采集 |
| Java → Agent | `/health`、`/stream`、`/ingest`、`/ingest/knowledge` | 探活、生成、灌库和知识入库 |

Java API 没有统一的 `/api` 前缀；`/api` 只是两个前端开发服务器的代理路径。生产反向代理也要把外部 `/api/...` 转发到 Java 的 `/...`。

## 仓库结构

```text
.
├── just_because_meme_understands_you-frontend/  # 用户网站
├── just_because_meme_understands_you-admin/     # 管理后台
├── just_because_meme_understands_you-backend/   # Java 业务服务
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-dev.yml.example
│       ├── application-prod.yml
│       └── db/
│           └── mysql.sql                        # MySQL 完整建表脚本
├── MemeAgent/
│   ├── app/                                   # AI 服务代码
│   ├── scripts/init_pgvector.sql
│   ├── environment.yml
│   └── .env.example
└── .env.example                               # 生产环境变量参考
```


## 1. 准备环境

| 依赖 | 要求 / 用途 |
| --- | --- |
| Git | 获取代码 |
| JDK | 17，与后端 `pom.xml` 一致 |
| Maven | 可直接使用仓库中的 Maven Wrapper，无需单独安装 |
| Node.js + npm | 使用兼容 Vue CLI 5 的环境；仓库未固定 Node.js 版本 |
| Python / Conda | 下文使用 Conda，环境文件指定 Python 3.11 |
| MySQL | 准备与业务 SQL 兼容的实例和完整表结构 |
| Redis | 后端缓存、限流、锁与消息功能使用 |
| PostgreSQL + pgvector | 使用 AI 向量检索时需要 |
| 外部服务凭据 | OSS；邮箱注册需 SMTP；AI 需模型 API 凭据 |

从 GitHub 仓库页面复制实际克隆地址，执行 `git clone` 后进入项目根目录。下文涉及目录切换的命令，除非另有说明，均从根目录开始。

默认服务地址：

| 服务 | 本地地址 |
| --- | --- |
| 用户网站 | http://localhost（80 端口） |
| 管理后台 | http://localhost:5174 |
| Java 后端 | http://localhost:8080 |
| MemeAgent | http://127.0.0.1:8000 |

## 2. 准备 MySQL 与 Redis

启动本地 MySQL 与 Redis，默认连接地址分别为 `127.0.0.1:3306`、`127.0.0.1:6379`。

在 MySQL 客户端中创建与项目配置一致的数据库。开发配置使用数据库名 `meme`：

```sql
CREATE DATABASE IF NOT EXISTS meme
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

执行完整建表脚本：

```bash
mysql -u root -p meme < just_because_meme_understands_you-backend/src/main/resources/db/mysql.sql
```

PowerShell 可以使用管道导入：

```powershell
Get-Content .\just_because_meme_understands_you-backend\src\main\resources\db\mysql.sql | mysql.exe -u root -p meme
```

Windows PowerShell 也可以先进入 MySQL 客户端，再执行：

```sql
SOURCE just_because_meme_understands_you-backend/src/main/resources/db/mysql.sql;
```

数据库准备完成后检查表数量：

```sql
USE meme;
SHOW TABLES;
```

至少应能看到 `user`、`user_auth`、`meme`、`meme_comment`、`meme_resource`、`meme_tag`、`user_favorite`、`user_notification`、`admin_knowledge`、`ai_chat_session` 和 `ai_chat_message`。

## 3. 配置并启动 Java 后端

在 `just_because_meme_understands_you-backend/src/main/resources/` 下创建 `application-dev.yml`。

仓库的 `application-dev.yml.example` 可用于了解配置项，但当前模板包含两个顶层 `meme:` 节点，直接复制可能产生重复键错误。首次配置可使用下面的合并版，替换所有 `CHANGE_ME` 值：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: "jdbc:mysql://127.0.0.1:3306/meme?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&rewriteBatchedStatements=true"
    username: root
    password: "CHANGE_ME_DB_PASSWORD"
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      # password: "CHANGE_ME_REDIS_PASSWORD"
  mail:
    host: smtp.qq.com
    port: 465
    username: "CHANGE_ME_MAIL_ADDRESS"
    password: "CHANGE_ME_SMTP_AUTH_CODE"

redisson:
  singleServerConfig:
    address: "redis://127.0.0.1:6379"

oss:
  endpoint: oss-cn-beijing.aliyuncs.com
  accessKeyId: "CHANGE_ME_OSS_ACCESS_KEY_ID"
  accessKeySecret: "CHANGE_ME_OSS_ACCESS_KEY_SECRET"
  bucketName: "CHANGE_ME_OSS_BUCKET_NAME"

jwt:
  secret: "CHANGE_ME_TO_A_RANDOM_SECRET_AT_LEAST_32_BYTES"
  cookie-secure: false

app:
  cors:
    allowed-origins: "http://localhost,http://localhost:80,http://127.0.0.1,http://localhost:5174,http://127.0.0.1:5174"
  feedback:
    to-email: "CHANGE_ME_FEEDBACK_MAIL_ADDRESS"

meme:
  agent:
    base-url: http://127.0.0.1:8000
    api-key: "CHANGE_ME_SHARED_INTERNAL_KEY"
  ai:
    backfill-enabled: false
  crawl:
    enabled: false
    schedule-enabled: false
```

配置说明：

- 本地默认 profile 为 `dev`，会自动加载上述文件。
- MySQL、Redis 必须可连接。Redis 如启用密码，填写 `spring.data.redis.password` 并核对实际客户端连接配置。
- SMTP 用于验证码、找回密码等功能；QQ 邮箱需使用 SMTP 授权码。反馈收件地址改为自己的邮箱。
- OSS 使用自己的 Bucket 与凭据，按桶地域调整 Endpoint，并为浏览器直传配置 CORS。项目使用 `tmp/` 临时对象前缀，可按模板说明设置生命周期。
- JWT 密钥使用至少 32 字节的随机字符串。上面的内容仅是占位符。
- 首次运行关闭批量回填入口和采集，待 AI 服务配置完成后再按需启用。

Windows PowerShell：

```powershell
cd just_because_meme_understands_you-backend
.\mvnw.cmd spring-boot:run
```

macOS / Linux：

```bash
cd just_because_meme_understands_you-backend
sh ./mvnw spring-boot:run
```

首次运行 Wrapper 会下载 Maven 与依赖。后端默认监听 `8080`。

## 4. 启动 MemeAgent

MemeAgent 只在使用 AI 搜梗、知识库或向量回填时需要启动。它单独使用 PostgreSQL + pgvector，和 Java 使用的 MySQL 不是同一个数据库。

### 4.1 准备 PostgreSQL + pgvector

准备一个启用了 `vector` 扩展的 PostgreSQL 实例，创建独立数据库。端口可以使用本机默认的 `5432`；如果用 Docker 映射到其他端口，连接串必须使用映射后的端口。

下面是 Docker Desktop 的本地示例：

```bash
docker run -d --name memeagent-pg \
  -e POSTGRES_USER=memeagent \
  -e POSTGRES_PASSWORD=CHANGE_ME_POSTGRES_PASSWORD \
  -e POSTGRES_DB=memeagent_vector \
  -p 5433:5432 \
  pgvector/pgvector:pg16
```

初始化表结构：

```bash
psql -h 127.0.0.1 -p 5433 -U memeagent -d memeagent_vector \
  -f MemeAgent/scripts/init_pgvector.sql
```

Agent 启动时也会执行建表逻辑，但数据库账号必须有创建 `vector` 扩展和表的权限。权限受限时，先由数据库管理员执行上面的 SQL。脚本和配置默认使用 `1536` 维向量，Embedding 模型的输出维度、`VECTOR_EMBEDDING_DIM` 和 PostgreSQL 向量列必须一致；已有表不会因为修改环境变量自动迁移。

### 4.2 创建 Python 环境

在项目根目录执行：

```bash
cd MemeAgent
conda env create -f environment.yml
conda activate memeagent
```

如果本机已经有 `memeagent` 环境，改用下面的命令同步依赖：

```bash
conda env update -n memeagent -f environment.yml --prune
conda activate memeagent
```

`environment.yml` 固定 Python 3.11，依赖包括 FastAPI、Uvicorn、Pydantic Settings、asyncpg、pgvector、LangChain、OpenAI 适配器和 Firecrawl 客户端。

### 4.3 创建并填写 `.env`

MemeAgent 只读取 `MemeAgent/.env`，不会读取仓库根目录的 `.env`。复制模板：

```powershell
# Windows PowerShell
Copy-Item .env.example .env
```

```bash
# macOS / Linux
cp .env.example .env
```

本地使用时至少填写以下项目：

```dotenv
APP_ENV=dev
APP_HOST=127.0.0.1
APP_PORT=8000
INTERNAL_API_KEY=CHANGE_ME_SHARED_INTERNAL_KEY
VECTOR_DATABASE_URL=postgresql://memeagent:CHANGE_ME_POSTGRES_PASSWORD@127.0.0.1:5433/memeagent_vector
VECTOR_EMBEDDING_DIM=1536
OPENAI_API_KEY=CHANGE_ME_MODEL_API_KEY
OPENAI_CHAT_MODEL=gpt-4o-mini
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
FIRECRAWL_ENABLED=false
LANGSMITH_TRACING=false
```

上面的连接串对应 Docker 示例的 `5433` 端口；如果 PostgreSQL 直接安装在本机并监听 `5432`，把连接串中的端口改成 `5432`。

配置规则：

- `INTERNAL_API_KEY` 不能为空，Agent 启动时会校验；它必须与 Java `meme.agent.api-key` 完全一致。
- `VECTOR_DATABASE_URL` 必须带用户名和密码。密码包含 `@`、`#`、`/` 等 URL 特殊字符时先进行 URL 编码。
- `OPENAI_API_KEY`、对话模型和 Embedding 模型是 AI 搜索的运行条件。使用其他 OpenAI 兼容服务时，再填写 `OPENAI_BASE_URL`，并确认该服务同时支持 Chat Completions 和 Embeddings。
- `FIRECRAWL_ENABLED=false` 可以先关闭热梗采集；开启采集时再填写 `FIRECRAWL_API_KEY` 以及 B 站地址等配置。
- LangSmith 不是运行必需项。只有需要链路追踪时才设置 `LANGSMITH_TRACING=true` 和 `LANGSMITH_API_KEY`。
- 如果单独设置 `INTERNAL_API_KEY_WRITE`，Java 的 `meme.agent.write-api-key` 也要填写同一个值；该密钥用于 `/ingest`、`/ingest/knowledge` 和 `/crawl/hot-memes`。

### 4.4 启动并检查服务

保持 `memeagent` 环境激活，并在 `MemeAgent` 目录执行：

```bash
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

启动日志会显示配置加载和向量库连接结果。另开终端检查：

```bash
curl http://127.0.0.1:8000/health
```

正常使用向量检索时，响应应包含 `"status":"ok"` 和 `"vector_db":true`。如果没有设置 `VECTOR_DATABASE_URL`，接口也可能返回 `status=ok`，但 `vector_db` 会是 `false`，这表示 Agent 只启动了 HTTP 服务，不能执行向量检索。开发环境的接口文档位于 http://127.0.0.1:8000/docs。

### 4.5 与 Java 后端联调

Java 的本地配置至少要对应：

```yaml
meme:
  agent:
    base-url: http://127.0.0.1:8000
    api-key: CHANGE_ME_SHARED_INTERNAL_KEY
    # 如果 Agent 设置了 INTERNAL_API_KEY_WRITE，再填写 write-api-key
    # write-api-key: CHANGE_ME_WRITE_KEY
```

推荐启动顺序是 MySQL、Redis、PostgreSQL → MemeAgent → Java 后端 → 两个前端。Java 通过 `X-Internal-Api-Key` 调用 Agent；读接口使用 `api-key`，灌库和采集使用 `write-api-key`，为空时才回退到读密钥。

向量库初始为空。将 Java 配置中的 `meme.ai.backfill-enabled` 设为 `true` 后重启 Java，再登录管理后台的「AI 运维 → 向量回填」触发已有梗入库。这个开关只控制是否允许回填接口，不会在 Agent 或 Java 启动时自动导入全部梗。

如果只体验社区基础功能，可以不启动 MemeAgent，并将 Java 的 `meme.agent.api-key` 留空；此时 AI 搜索、知识库入库和向量回填不可用。

## 5. 启动用户前端

新开终端，从项目根目录执行：

```bash
cd just_because_meme_understands_you-frontend
npm install
npm run serve
```

打开 http://localhost。开发服务器把 `/api` 请求代理到 `http://localhost:8080`，并去掉 `/api` 前缀。

若 80 端口被占用或没有绑定权限，可以改用：

```bash
npm run serve -- --port 8081
```

此时访问 http://localhost:8081，并在后端 CORS 白名单加入该 Origin。若启用 GitHub OAuth，也需把前端回调地址改为 `http://localhost:8081/login/oauth/callback`。

## 6. 启动管理后台

新开终端，从项目根目录执行：

```bash
cd just_because_meme_understands_you-admin
npm install
npm run serve
```

打开 http://localhost:5174。管理端已经提供 `.env.development`，使用 `VUE_APP_API_BASE_URL=/api` 和 `VUE_APP_API_TIMEOUT=15000`；后台同样通过 `/api` 代理访问 Java 服务。

登录账号需要 `ROLE_ADMIN` 角色，请由数据库初始化或已有管理员完成授权。普通注册账号不能直接进入管理后台；AI 运维白名单也不等同于管理后台角色。本文不提供默认管理员账号或密码。

## 可选功能

### GitHub 登录

在 GitHub OAuth App 中配置后端回调地址 `http://localhost:8080/login/oauth/github/callback`，在 Java 的 `app.oauth.github` 下填写：

```yaml
client-id: "CHANGE_ME_GITHUB_CLIENT_ID"
client-secret: "CHANGE_ME_GITHUB_CLIENT_SECRET"
redirect-uri: http://localhost:8080/login/oauth/github/callback
frontend-callback-url: http://localhost/login/oauth/callback
```

以上是 `app.oauth.github` 的子配置，不要新增重复的顶层 `app:`。未配置时使用普通账号登录流程。

### 热梗采集与追踪

- 采集：在 MemeAgent 中配置 Firecrawl，设置 `FIRECRAWL_ENABLED=true`；在 Java 的已有 `meme.crawl` 节点中启用 `enabled`，设置 `publisher-user-id` 为真实存在的发布账号 ID。需要定时执行时再启用 `schedule-enabled`。
- 追踪：在 MemeAgent 中按需填写 `LANGSMITH_API_KEY`、`LANGSMITH_PROJECT`，启用 `LANGSMITH_TRACING`。

## 启动后检查

1. 打开用户首页，确认列表请求成功；初始化数据库无内容时，空列表属于正常情况。
2. 访问 `http://localhost:8080/list?page=1&size=1`，检查业务响应和后端日志，确认 MySQL / Redis 正常。
3. 完成注册或登录；注册验证码发送失败时检查 SMTP 配置。
4. 使用管理员账号登录管理后台，确认权限与数据加载正常。
5. 使用 AI 功能时检查 Agent 探活、内容入库和一次实际 AI 搜梗。

## 构建与部署

两个前端分别在各自目录执行：

```bash
npm run build
```

构建产物位于各模块 `dist/`。用户前端部署在站点根路径，管理前端的生产 `publicPath` 为 `/admin/`，反向代理需要匹配这个路径，并支持 Vue Router 的 history 回退。

两个前端实际使用的 API 环境变量均为 `VUE_APP_API_BASE_URL`，默认 `/api`。需要覆盖时，在对应前端目录的 `.env.production.local` 中设置，随后重新构建：

```dotenv
VUE_APP_API_BASE_URL=/api
```

用户端当前的 `.env.production.example` 示例写的是 `VUE_APP_API_BASE`，与代码读取名称不一致。部署用户端时请使用 `VUE_APP_API_BASE_URL`。

后端在模块目录打包：

```powershell
# Windows；macOS / Linux 改用 sh ./mvnw package
.\mvnw.cmd package
```

向运行进程注入根目录 `.env.example` 所列的生产环境变量后启动：

```bash
java -jar target/just_because_meme_understands_you-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

生产需要配置数据库、Redis、OSS、JWT 和明确的 `CORS_ALLOWED_ORIGINS`；邮箱和 AI 等按启用功能配置。不要只复制 `.env` 后直接执行 Java，环境变量需由终端、进程管理器或部署平台显式注入。

反向代理需将 `/api/` 转发到 Java 并移除 `/api`，对 SSE 关闭响应缓冲并设置合理超时。MemeAgent 应由 Java 通过内网访问；生产启动去掉 `--reload`。容器部署时，`127.0.0.1` 指向当前容器，数据库与服务地址需按实际网络修改。

## 常见问题

| 现象 | 检查项 |
| --- | --- |
| `DuplicateKeyException` / YAML 重复键 | 开发模板的两个 `meme:` 必须合并为一个；可使用本文配置 |
| 缺少 `DB_URL`、`JWT_SECRET` 等配置 | 确认本地 `application-dev.yml` 已创建并生效，或环境变量已注入 |
| MySQL 报表不存在 | 确认已执行 `just_because_meme_understands_you-backend/src/main/resources/db/mysql.sql`，并且 JDBC URL 使用的是同一个 `meme` 数据库 |
| Redis 连接失败 | 检查服务、地址、端口、密码以及客户端实际配置 |
| 前端请求 404 / 代理失败 | 确认后端监听 8080，检查 `/api` 代理及路径重写 |
| 前端跨域或登录 Cookie 异常 | 使用一致的主机名，核对 Origin 白名单；本地 HTTP 保持 `jwt.cookie-secure=false` |
| Agent 启动时报数据库认证失败 | `VECTOR_DATABASE_URL` 需要完整账号密码和正确端口，不能省略用户名；URL 特殊字符需要编码 |
| Agent `/health` 返回 `vector_db=false` | PostgreSQL 未连接、连接串端口错误，或 Agent 账号没有 `vector` 扩展和建表权限 |
| Agent 返回 401 / 403 | 检查 Java 与 Agent 的读写密钥是否匹配；请求头名称必须是 `X-Internal-Api-Key` |
| Agent 启动后 AI 生成失败 | 检查 `OPENAI_API_KEY`、`OPENAI_BASE_URL`、对话模型和 Embedding 模型是否可用 |
| 向量维度不匹配 | 模型输出、环境变量与已有向量表列维度必须相同 |
| AI 检索为空或生成失败 | 检查内容是否已入库、模型凭据和模型服务日志 |
| 上传图片失败 | 检查 OSS 凭据、Bucket 权限、地域、上传域名及浏览器直传 CORS |
| 管理后台拒绝登录 | 检查账号是否拥有 `ROLE_ADMIN` |
