# Docker Compose 单机部署

**针对你的 Alibaba Cloud Linux 4、2 核 / 2 GiB、公网 IP `8.148.12.121` 的逐步操作，见 [服务器部署流程](SERVER_DEPLOYMENT.md)。** 包含安装、Swap、本地 `.env` 上传、数据导入、管理员初始化、验收、备份和 PostgreSQL 大版本升级。

本目录将项目运行所需服务统一编排到一台 Linux 服务器：MySQL、Redis、PostgreSQL/pgvector、Spring Boot、FastAPI MemeAgent、用户端 Vue、管理端 Vue 和 Nginx。网站由 Nginx 对外提供 HTTP；三个数据库端口仅绑定服务器的 `127.0.0.1`，可通过 SSH 隧道管理，不会监听公网网卡。Java 和 FastAPI 不映射到宿主机端口。

用户端 Vue 与管理端 Vue 在镜像构建时从源码编译，由 Nginx 分别托管在 `/` 和 `/admin/`。Nginx 将 `/api/` 转发至 Spring Boot，并关闭 SSE 响应缓冲。

## 服务器要求

- Linux 服务器。默认参数面向 2 核 / 2 GiB 的低流量试运行，需额外配置 4 GiB Swap；长期运行建议至少 4 GiB 内存。默认 Java 堆上限为 320 MiB，容器总内存上限为 640 MiB；大文件解析、大量向量入库或并发请求需要扩容并调整 `.env`。
- Docker Engine 与 Docker Compose v2（需支持 `docker compose up --wait`）。
- 防火墙按需开放 SSH `22` 和网站 `HTTP_PORT`；不要开放 MySQL 3306、Redis 6379、PostgreSQL 5432、Java 8080 或 FastAPI 8000。SSH 来源建议限制为运维电脑的公网 IP。
- 外部模型服务和阿里云 OSS 的 API 凭据。

## 首次部署

在服务器克隆仓库后进入本目录：

```bash
git clone <你的 GitHub 仓库地址> just_because_meme_understands_you
cd just_because_meme_understands_you/deploy
cp env.production.example .env
chmod 600 .env
```

编辑 `.env`，至少填写以下值：

- MySQL root 密码、MySQL 应用账号密码、Redis 密码、PostgreSQL 密码、MemeAgent 内部密钥和 JWT 密钥。可分别运行 `openssl rand -hex 32` 生成；PostgreSQL 密码必须使用这种十六进制格式，因为它会拼入连接 URL。
- OSS AccessKey ID、AccessKey Secret 和 Bucket 名称。
- `OPENAI_API_KEY`，以及与模型服务商相符的 `OPENAI_BASE_URL`、聊天模型、Embedding 模型和向量维度。
- `CORS_ALLOWED_ORIGINS`、GitHub OAuth 回调地址等站点相关配置。GitHub OAuth 回调需使用公开地址加 `/api/v1/auth/oauth/github/callbacks`。

启动并等待所有容器通过健康检查：

```bash
bash ./deploy.sh
```

脚本会先检查必填项、安静校验 Compose 配置，再串行构建 Java、MemeAgent、用户端和管理端镜像并验证 Nginx 语法，最后启动全部服务。访问 `http://服务器地址/`，管理端访问 `http://服务器地址/admin/`。如将 `HTTP_PORT` 改成 `8081`，访问地址相应改为 `http://服务器地址:8081/`。

## 服务和数据

| 服务 | 用途 | 对外端口 | 持久化 |
|---|---|---:|---|
| Nginx | Vue 静态资源、API 反向代理、SSE 转发 | `HTTP_PORT` | 无 |
| Spring Boot | Java 业务 API、Flyway 迁移 | 无 | MySQL/Redis |
| MemeAgent | FastAPI 检索、生成与向量入库 | 无 | PostgreSQL |
| MySQL 8.4 | 项目业务库 `meme` | `127.0.0.1:3306`（仅 SSH 隧道） | `mysql-data` |
| Redis 7 | 限流、缓存与任务状态 | `127.0.0.1:6379`（仅 SSH 隧道） | `redis-data` |
| PostgreSQL 17 + pgvector | HNSW 梗向量与知识向量 | `127.0.0.1:5432`（仅 SSH 隧道） | `postgres-data` |

首次创建 MySQL 数据卷时，容器会导入后端的 `mysql.sql` 基线结构；Spring Boot 随后通过 Flyway 应用未执行迁移。MySQL 基线脚本只会在空数据卷初始化时运行。MemeAgent 首次启动会创建所需的 pgvector 扩展、表和索引。

命名卷会在重建容器后保留。普通停止和启动不会删除数据：

```bash
docker compose down
docker compose up -d
```

**不要对正式数据运行 `docker compose down -v`**，该命令会删除 MySQL、Redis 和 PostgreSQL 的数据卷。定期将数据库备份复制到服务器以外的位置。

## 通过 SSH 隧道访问数据库

数据库只监听服务器本机回环地址。先在本地 PowerShell 建立 SSH 隧道，保持该终端窗口运行；将 `SSH_USER` 和 `SERVER_IP` 替换为实际 SSH 用户名和服务器地址：

```powershell
ssh -N -L 13306:127.0.0.1:3306 -L 16379:127.0.0.1:6379 -L 15432:127.0.0.1:5432 SSH_USER@SERVER_IP
```

然后在 DataGrip 或数据库客户端连接本地地址：MySQL 使用 `127.0.0.1:13306`，Redis 使用 `127.0.0.1:16379`，PostgreSQL 使用 `127.0.0.1:15432`；认证仍使用 `.env` 中对应的数据库用户名和密码。关闭 SSH 窗口会终止隧道。安全组无需开放三个数据库端口，只需按需允许 SSH 端口，并限制可信来源。

示例备份命令：

```bash
mkdir -p /var/backups/meme
chmod 700 /var/backups/meme
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers --no-tablespaces --set-gtid-purged=OFF meme' > /var/backups/meme/meme-$(date +%F).sql
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" --no-owner --no-acl "$POSTGRES_DB"' > /var/backups/meme/memeagent-vector-$(date +%F).sql
```

此部署会创建全新的数据库卷，不会自动搬运你电脑上已有的 MySQL 业务数据或 PostgreSQL 向量数据。若要带历史数据上线，先为当前数据库做备份，再按当前代码版本制定恢复步骤；向量索引可在数据恢复后重新回填。

## 日常运维

```bash
# 查看所有容器状态
docker compose ps

# 查看应用和数据库日志
docker compose logs -f backend meme-agent nginx mysql redis postgres

# 拉取代码后重新构建并更新容器
git pull
bash ./deploy.sh

# 停止服务但保留数据卷
docker compose down
```

`deploy.sh` 会在 `.env` 不存在时生成模板并退出；填写完密钥后再次运行即可。真实 `.env` 被 Git 忽略，`.dockerignore` 也会阻止它进入镜像构建上下文。

## 使用 GitHub Actions 手动部署

仓库包含 `Docker Compose 部署` 工作流。将工作流合并到 GitHub 仓库默认分支后，可在仓库 **Actions → Docker Compose 部署 → Run workflow** 中选择要部署的分支，手动触发服务器部署。工作流通过 SSH 更新服务器上的专用仓库目录，然后调用本目录的 `deploy.sh`；镜像仍由服务器上的 Docker Compose 构建和启动。

先在服务器准备一次部署目录：克隆仓库到一个绝对路径（例如 `/opt/just_because_meme_understands_you`），在该目录的 `deploy/` 下复制 `env.production.example` 为 `.env` 并填写生产配置。服务器必须已安装 Docker Engine、Docker Compose v2，并允许 SSH 登录用户执行 Docker 命令。若 GitHub 仓库是私有仓库，还需让服务器上的 Git 能使用只读 Deploy Key 或其他只读凭据拉取仓库；该凭据与 Actions 登录服务器用的 SSH 私钥是两回事。

然后在 GitHub 仓库 **Settings → Secrets and variables → Actions → New repository secret** 中配置：

| Secret 名称 | 内容 |
|---|---|
| `DEPLOY_HOST` | 服务器域名或公网 IP |
| `DEPLOY_USER` | SSH 登录用户名 |
| `DEPLOY_SSH_PRIVATE_KEY` | Runner 登录服务器的私钥（完整多行内容） |
| `DEPLOY_SSH_KNOWN_HOSTS` | 服务器对应的 `known_hosts` 公钥记录；请先通过可信渠道核对主机指纹 |
| `DEPLOY_PATH` | 服务器上仓库的绝对路径 |
| `DEPLOY_SSH_PORT` | 可选；SSH 端口，未配置时默认为 `22` |

Actions 工作流只在手动运行时部署。GitHub 要求手动运行的工作流文件存在于默认分支；建议先确认目标分支的“项目质量检查”工作流通过，再从 Actions 页面选择同一分支。服务器工作区必须保持干净；工作流会拒绝覆盖未提交改动。`.env` 被 Git 忽略，正常情况下不会阻止更新。

## HTTPS 与公网部署

本配置默认只提供 HTTP。正式公网部署应在 Nginx 前配置 HTTPS（例如服务器上的证书与 TLS 监听），并将 `JWT_COOKIE_SECURE=true`、`HSTS_ENABLED=true`，同时把 OAuth 回调和前端地址改为 HTTPS。未启用 TLS 时，只建议在可信内网临时使用。

Redis 和数据库网络与 Web 网络隔离，Nginx 不能直接连接它们；后端和 MemeAgent 才能访问数据网络。MySQL、Redis、PostgreSQL 的宿主机映射只绑定 `127.0.0.1`，Spring Boot 和 FastAPI 没有宿主机端口映射。

## 常见排查

```bash
# 检查服务是否健康
docker compose ps

# 查看后端启动、Flyway 和连接池日志
docker compose logs --tail=200 backend

# 查看 MemeAgent 向量数据库初始化日志
docker compose logs --tail=200 meme-agent postgres

# 校验环境变量和 Compose 文件，不启动容器
docker compose config --quiet
```

若 MySQL 密码、PostgreSQL 密码或向量模型维度在已有数据卷创建后发生变化，修改 `.env` 不会重置数据库账号或迁移已有向量列；请按备份和数据库变更流程操作，不要删除数据卷来“修复”配置。
