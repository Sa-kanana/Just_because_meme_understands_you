# 在你的阿里云服务器部署项目

当前目标：Alibaba Cloud Linux 4 LTS 64 位、2 核 / 2 GiB、40 GiB 系统盘、公网 IP `8.148.12.121`。以下以 `root` 和 `/opt/Just_because_meme_understands_you` 为例；SSH 用户不是 root 时请替换账号，并对系统安装命令使用 sudo。

本次修改必须先提交并推送到 GitHub，服务器才会拿到修复后的部署文件。生产密钥 `deploy/.env` 不应提交；下文通过 SCP 单独上传。

## 1. 放行入口并连接服务器

在阿里云 ECS 安全组放行 TCP `22` 和 `80`。22 的来源设置为你的运维电脑公网 IP；80 为网站访问入口。后续启用 HTTPS 再放行 `443`。MySQL、Redis、PostgreSQL、Java、FastAPI 不需要开放端口。

在 Windows PowerShell 运行：

```powershell
# 使用截图中的 ECS 公网 IP；首次连接时核对服务器主机指纹。
ssh root@8.148.12.121
```

在本地另开一个 PowerShell 窗口，可通过 SSH 隧道访问数据库；隧道窗口需保持运行：

```powershell
ssh -N -L 13306:127.0.0.1:3306 -L 16379:127.0.0.1:6379 -L 15432:127.0.0.1:5432 root@8.148.12.121
```

DataGrip 或其他客户端连接本机 `127.0.0.1`：MySQL 端口 `13306`、Redis 端口 `16379`、PostgreSQL 端口 `15432`。登录凭据使用服务器 `deploy/.env` 中对应的值。安全组只需允许可信来源访问 SSH `22`，不要开放数据库端口。

后续标为 Bash 的命令都在服务器执行。`192.168.100.128` 是之前的容器地址，不作为生产服务连接地址。Compose 内部始终使用 `mysql:3306`、`redis:6379`、`postgres:5432` 和 `meme-agent:8000`，容器重建后不需要修改 IP。

## 2. 安装 Docker Engine、Compose 与基础工具

```bash
# 核对实际操作系统，以下安装步骤适用于 Alibaba Cloud Linux 4 RPM 版。
cat /etc/os-release
# 查看现有安装；两条命令都正常时，直接跳到安装基础工具。
docker --version
docker compose version
```

**仅在尚未安装 Docker、Moby 或其他 Docker Engine 时执行下面的安装块**。已有可用引擎无需卸载或更换。若已安装 Moby 但缺少 Compose 插件，先按阿里云文档为现有引擎安装插件，避免混装引擎。

```bash
# 安装下载 Docker 软件源所需工具。
dnf install -y curl ca-certificates
# 使用 Docker CE 的阿里云镜像源。
curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo \
  -o /etc/yum.repos.d/docker-ce.repo
# 仅将 Docker CE 软件源的 CentOS 兼容版本固定为 9，不修改系统其他软件源。
sed -i 's|$releasever|9|g' /etc/yum.repos.d/docker-ce.repo
# 安装引擎、命令行、构建插件和 Compose v2 插件。
dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
# 启动 Docker，并使其随服务器开机启动。
systemctl enable --now docker
```

此安装方式依据[阿里云的 Alibaba Cloud Linux 4 Docker 安装说明](https://www.alibabacloud.com/help/en/ecs/user-guide/install-and-use-docker)。

```bash
# 克隆代码、编辑配置和生成随机密钥所需的工具。
dnf install -y git nano openssl curl
# 确认当前用户能访问引擎；权限错误时不要继续部署。
docker info >/dev/null
# 确认 Compose v2 和 Buildx 可用。
docker compose version
docker buildx version
```

## 3. 为 2 GiB 服务器准备 4 GiB Swap

```bash
# 检查磁盘、内存和已有交换空间；保留足够空间用于镜像与数据库。
df -h /
free -h
swapon --show
# 只在专用交换文件不存在时创建，不覆盖已有文件。
if [ ! -e /swapfile-meme ]; then
  fallocate -l 4G /swapfile-meme
  chmod 600 /swapfile-meme
  mkswap /swapfile-meme
  swapon /swapfile-meme
  # 记录开机挂载规则，避免重启后失去交换空间。
  if ! grep -q '^/swapfile-meme ' /etc/fstab; then
    printf '/swapfile-meme none swap sw 0 0\n' >> /etc/fstab
  fi
fi
# 验证 Swap 已激活；已有同名文件但未激活时，应先核对其用途，不能重新格式化。
free -h
swapon --show
```

默认六个容器的内存上限合计约 1856 MiB，此外还有系统和镜像构建开销。Swap 用于缓解启动和构建峰值，会增加延迟；它不能替代物理内存。当前配置适合低流量试运行。长期运行建议至少升级至 4 GiB；大量知识文件索引或高并发 AI 搜索建议 8 GiB，并同步提高 `.env` 中的容器上限和 Java 堆上限。

## 4. 克隆项目

```bash
# 将代码放在独立的生产部署目录。
mkdir -p /opt
git clone https://github.com/Sa-kanana/Just_because_meme_understands_you.git \
  /opt/Just_because_meme_understands_you
# 后续 Compose 命令均在 deploy 目录执行。
cd /opt/Just_because_meme_understands_you/deploy
```

目录已存在时，不再 clone；确认部署目录没有人工修改后，在仓库中执行 `git pull --ff-only`。私有仓库需要服务器自己的只读 GitHub Deploy Key，或通过 GitHub 认可的方式认证；服务器拉取仓库的凭据与 Actions 登录服务器的 SSH 密钥用途不同。

## 5. 上传并核对 `.env`

本地 `deploy/.env` 的现有凭据和模型配置已保留，并补入小内存部署参数。**另开一个 Windows PowerShell 窗口**执行：

```powershell
# 回到本地项目根目录。
Set-Location 'D:\Code\Just_because_meme_understands_you'
# 将密钥通过 SSH 上传；首次部署使用，后续不要用旧配置覆盖生产密码。
scp '.\deploy\.env' root@8.148.12.121:/opt/Just_because_meme_understands_you/deploy/.env
```

然后回到服务器终端：

```bash
cd /opt/Just_because_meme_understands_you/deploy
# 密钥只允许部署账号读取。
chmod 600 .env
# 在服务器编辑配置，不要把完整文件粘贴到聊天或公开日志。
nano .env
```

没有现成 `.env` 时，可改为 `cp env.production.example .env` 后填写全部必填项。下面只列公开地址和非敏感参数；数据库密码、内部 API Key、JWT、OSS 和模型 Key 使用各自的真实值。每个自建密码分别运行一次 `openssl rand -hex 32` 生成。

```dotenv
# 服务器对外 HTTP 端口。
HTTP_PORT=80
# 2 GiB 试运行的 Java 堆范围；容器内存上限另由 BACKEND_MEMORY_LIMIT 决定。
JAVA_OPTS=-Xms128m -Xmx320m -XX:+UseContainerSupport
# 当前浏览器站点 Origin；使用域名后改为实际域名及协议。
CORS_ALLOWED_ORIGINS=http://8.148.12.121
# 用户端和 API 同源，关闭额外的跨域访问。
CORS_ENABLED=false
# 暂时使用 HTTP 时不启用 Secure Cookie。
JWT_COOKIE_SECURE=false
# 没有 HTTPS 时不启用 HSTS。
HSTS_ENABLED=false
# GitHub OAuth 回调地址；只有配置了 GitHub OAuth App 才启用该功能。
GITHUB_REDIRECT_URI=http://8.148.12.121/api/v1/auth/oauth/github/callbacks
# OAuth 成功后返回用户端的地址。
GITHUB_FRONTEND_CALLBACK_URL=http://8.148.12.121/login/oauth/callback
# 新数据库中还没有注册账号，第一次启动必须留空。
AUTH_BOOTSTRAP_SUPER_ADMIN_EMAIL=
```

注意：

- `POSTGRES_PASSWORD` 要使用 URL 安全字符，推荐十六进制随机值；它会写入 PostgreSQL 连接 URL。
- `OPENAI_BASE_URL`、聊天模型、Embedding 模型必须是你所用服务的真实配置，`VECTOR_EMBEDDING_DIM` 要等于 Embedding 的实际输出维度。当前本地配置保持原来的模型与 `1024` 维，未替换为模板里的 OpenAI 模型。
- `MAIL_USERNAME`、`MAIL_PASSWORD` 是邮件验证码注册和找回密码所需配置。新站点需要这些功能时必须配置 SMTP；仅能启动页面不代表邮箱注册可用。
- Compose 已固定数据库内网地址；旧 `.env` 中若残留 `DB_URL` 或 `REDIS_HOST=192.168.100.128`，不会覆盖 Compose 注入的 `mysql` 和 `redis` 地址。
- 数据卷初始化后修改 `.env` 的 MySQL/PostgreSQL 密码，不会修改库中已存在的账号密码；不能靠改配置完成密码轮换。
- `.env` 只使用一行一个 `KEY=VALUE`，同名配置不能重复，不要用 Shell 命令生成值或 `source .env`。

## 6. 选择空库上线，或导入现有数据

**空库上线：** 直接跳到第 7 步。MySQL 首次创建数据卷时导入仓库 `mysql.sql`，然后后端通过 Flyway 执行增量迁移。你电脑里的业务数据不会自动上传。

**保留电脑里的数据：** 在启动后端前，准备完整 MySQL 备份，包含表结构、数据和 `flyway_schema_history`。不能只导出 INSERT，也不要手工清空迁移历史。在本地 Windows PowerShell 导出时，使用 `mysqldump.exe --result-file` 让 MySQL 客户端直接写入 UTF-8 字节；不要使用 `>`、`Out-File` 或 `Set-Content` 写 dump，否则 PowerShell 可能改写文件编码：

```powershell
# 备份文件直接保存到当前用户目录，避免写入项目并误提交到 Git。
$dumpFile = Join-Path $env:USERPROFILE 'meme-full.sql'
# -h、-P、-u 分别填写源 MySQL 地址、端口和账号；密码通过 -p 交互输入，不写进命令。
# 将 SOURCE_HOST 和 SOURCE_USER 替换成你本机数据库的实际连接信息。
# --default-character-set 固定客户端和连接使用 utf8mb4。
# --single-transaction 导出 InnoDB 一致性快照，并保留例程、触发器、二进制内容和数据库名。
# --result-file 由 mysqldump 直接写字节，避免 PowerShell 文本重定向转换编码。
mysqldump.exe -h SOURCE_HOST -P 3306 -u SOURCE_USER -p --default-character-set=utf8mb4 --single-transaction --routines --triggers --hex-blob --no-tablespaces --set-gtid-purged=OFF --databases meme --result-file="$dumpFile"
# 导出命令失败时停止，不要上传不完整备份。
if ($LASTEXITCODE -ne 0) { throw 'mysqldump 导出失败，请检查客户端输出。' }
# 通过 SCP 原样上传 SQL 文件；SCP 不会转换文件内容编码。
scp.exe $dumpFile root@8.148.12.121:/root/meme-full.sql
```

将备份上传到服务器 `/root/meme-full.sql` 后执行以下服务器命令。**恢复仅用于新的空生产库；完整备份通常含 DROP TABLE，会替换目标库内容。**

```bash
cd /opt/Just_because_meme_understands_you/deploy
# 恢复命令出现错误立即停止，不能继续启动应用或重复覆盖目标库。
set -e
# 先校验配置，再只启动数据库；暂不启动 Java、FastAPI 和网站。
docker compose config --quiet
docker compose pull mysql redis postgres
docker compose up -d --wait --wait-timeout 300 mysql redis postgres
# 从服务器本地备份文件恢复业务库；不会在命令行显示数据库密码。
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --default-character-set=utf8mb4 -uroot meme' < /root/meme-full.sql
# 验证业务库迁移历史；旧迁移记录应全部 success=1。
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot meme -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
```

后端启动后只执行尚未执行的迁移。恢复的历史若含失败记录或校验和不匹配，应先解决具体迁移错误，不能直接 repair 掩盖结构差异。若源库从未接入 Flyway，先确认它与项目基线相符，再启动迁移。

PostgreSQL 向量数据单独备份恢复，不包含在 MySQL 备份中。也可在业务库导入完成后，通过管理端知识库索引及梗向量对账功能重新建立向量；只有 MySQL 数据而未回填向量时，AI 召回不会自动得到完整旧内容。

## 7. 一键构建并启动

```bash
cd /opt/Just_because_meme_understands_you/deploy
# 只校验配置，不打印解析后的密码与密钥。
docker compose config --quiet
# 检查配置、拉取数据库镜像、串行构建应用，最后启动并等待所有服务健康。
bash ./deploy.sh
```

首次运行可能较慢，取决于镜像仓库、Maven/npm/pip 下载速度和服务器 Swap 使用量。健康等待默认 900 秒，构建耗时不计入该值。国内服务器若拉取 Docker Hub 超时，应在阿里云容器镜像服务获取你自己的镜像加速配置；不要把与其他用户有关的镜像源写入项目。

Vue 不需要在服务器安装 Node，Java 不需要在服务器安装 JDK，FastAPI 不需要在服务器安装 Python 依赖；这些都在镜像内构建和运行。宿主机只需 Docker、Compose 和基本运维工具。

## 8. 验收服务和业务功能

```bash
cd /opt/Just_because_meme_understands_you/deploy
# 六个服务都应处于 running / healthy。
docker compose ps
# 查看启动和迁移日志。
docker compose logs --tail=120 backend meme-agent
# 检查两个静态页面入口。
curl -I http://127.0.0.1/
curl -I http://127.0.0.1/admin/
# 验证 Nginx 去掉 /api 前缀后能访问公开标签 API；响应应 code=1。
curl -fsS http://127.0.0.1/api/v1/tags
# 验证向量库连通；应 status=ok 且 vector_db=true。
docker compose exec -T meme-agent python -c \
  'import json,urllib.request; print(json.load(urllib.request.urlopen("http://127.0.0.1:8000/health")))'
# 验证 Flyway 没有失败的迁移记录。
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot meme -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
# 观察物理内存、Swap 和容器内存。
free -h
docker stats --no-stream
```

浏览器访问：

- 用户端：`http://8.148.12.121/`
- 管理端：`http://8.148.12.121/admin/`

逐项操作：刷新一个用户端详情路由、刷新管理端路由、注册/登录、退出、上传图片、发布内容、管理员审核、AI 搜索、停止生成、知识文件索引。容器健康仅证明服务和数据库可达，**不会验证模型 Key、SMTP 或 OSS 的外部服务权限**，这些要通过对应业务操作验收。

若本机 `curl` 正常而公网不能访问，检查 ECS 安全组、宿主机防火墙和 `HTTP_PORT`。主机启用了 firewalld 时可执行 `firewall-cmd --permanent --add-port=80/tcp` 和 `firewall-cmd --reload`。

## 9. 初始化管理员和 OSS 上传

新库第一次启动保持 `AUTH_BOOTSTRAP_SUPER_ADMIN_EMAIL` 为空。先在用户端注册邮箱账号，再在服务器 `.env` 填入该邮箱，然后仅重建后端容器：

```bash
cd /opt/Just_because_meme_understands_you/deploy
# 仅修改 AUTH_BOOTSTRAP_SUPER_ADMIN_EMAIL 为已注册的邮箱。
nano .env
# 应用环境变量变化需要重建容器；单纯 restart 不会加载新的 Compose 环境变量。
docker compose up -d --no-deps --wait backend
# 检查引导和启动是否成功，再用该账号登录管理端。
docker compose logs --tail=100 backend
```

超级管理员创建后可清空引导邮箱并再次重建后端。不存在的引导邮箱会导致启动失败，因此不要在空库上直接填写一个尚未注册的邮箱。

在阿里云 OSS 配置浏览器直传 CORS：允许来源 `http://8.148.12.121`，允许方法 `POST`、`GET`、`HEAD`，允许请求 Header 为 `*`，暴露 Header 为 `ETag`。使用自定义公开图片域名时，填写对应 `OSS_PUBLIC_BASE_URL`；浏览器上传域名由 `OSS_UPLOAD_HOST` 控制。图片公开 URL 或 CDN 回源权限必须可用。

OSS 临时文件生命周期规则只匹配项目实际的 `tmp/` 前缀；不能把整个 Bucket 的所有图片配置成几天后删除，否则已发布图片也会被清掉。删除正在引用的图片前应由项目资产引用和清理流程判断。

## 10. 更新、停止、备份和恢复边界

更新前先备份，后端更新可能执行不可自动回滚的数据库迁移：

```bash
cd /opt/Just_because_meme_understands_you/deploy
# 备份或更新失败时立即停止，不在失败备份后继续部署。
set -e
# 限制新备份文件的读取权限。
umask 077
# 备份放在 Git 仓库以外，避免触发 Actions 的工作区保护。
mkdir -p /var/backups/meme
# 分别保存业务库、迁移历史及向量库；确认命令成功后再更新。
docker compose exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --default-character-set=utf8mb4 -uroot --single-transaction --routines --triggers --hex-blob --no-tablespaces --set-gtid-purged=OFF --databases meme' \
  > /var/backups/meme/mysql-$(date +%Y%m%d-%H%M%S).sql
docker compose exec -T postgres sh -c \
  'exec pg_dump -U "$POSTGRES_USER" --no-owner --no-acl "$POSTGRES_DB"' \
  > /var/backups/meme/postgres-$(date +%Y%m%d-%H%M%S).sql
# 环境文件是恢复数据库账号和外部服务访问所需的单独备份。
cp .env /var/backups/meme/deploy.env.$(date +%Y%m%d-%H%M%S)
# 确认当前工作区干净，再快进更新代码。
git status --short
git pull --ff-only
# 重新构建和启动；不会主动执行 down 或删除数据卷。
bash ./deploy.sh
```

备份需要再复制到服务器之外。恢复 MySQL/PostgreSQL 前停止业务写入并核对目标库，不在仍有用户请求时覆盖数据。代码回退不等于数据库迁移回退；若新版本改了数据库，必须同时考虑备份恢复及旧代码兼容性。

```bash
# 停止容器，保留三个数据库的数据卷。
docker compose down
# 恢复运行现有镜像，等待健康检查。
docker compose up -d --wait --wait-timeout 900
```

不要运行 `docker compose down -v` 或清理数据库卷。重建容器并不需要删除卷。`deploy.sh` 不会自动清理旧镜像或构建缓存；磁盘不足时先查看 `docker system df`，确认无用的构建缓存后再处理，不能混用包含卷删除的清理命令。

## 11. 已有 PostgreSQL 16 数据卷的升级

新安装无需执行这一节。新配置使用 PostgreSQL 17，应用连接参数依赖该版本。PostgreSQL 16 数据目录不能直接交给 17 镜像；部署脚本会拒绝这样启动。

已有旧 postgres 容器时，先用旧容器导出，再用新卷恢复。下列命令保留旧数据卷，不执行卷删除：

```bash
cd /opt/Just_because_meme_understands_you/deploy
# 大版本迁移任何步骤失败时立即停止，保留旧容器、旧卷和备份进行排查。
set -e
umask 077
mkdir -p /var/backups/meme
# 停止写入向量库的应用进程。
docker compose stop nginx backend meme-agent
# 获取旧 postgres 容器；容器不存在时需要单独从旧卷启动 PG16 完成导出，不能跳过备份。
old_postgres_container="$(docker compose ps --all --quiet postgres)"
test -n "$old_postgres_container"
# docker start 沿用旧容器的 PG16 镜像，不使用新 Compose 镜像。
docker start "$old_postgres_container"
# 最多等待约 120 秒；不无限等待启动失败的旧库。
for attempt in $(seq 1 60); do
  if docker exec "$old_postgres_container" sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'; then
    break
  fi
  sleep 2
done
# 最终探活必须成功，否则在导出前退出。
docker exec "$old_postgres_container" sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
# 导出到固定备份路径，保留旧卷作为额外恢复来源。
docker exec "$old_postgres_container" sh -c \
  'exec pg_dump -U "$POSTGRES_USER" --no-owner --no-acl "$POSTGRES_DB"' \
  > /var/backups/meme/postgres-before-pg17.sql
# 核对备份非空，停止旧库。
test -s /var/backups/meme/postgres-before-pg17.sql
docker stop "$old_postgres_container"
# 将 POSTGRES_VOLUME_NAME 改为 just-because-meme_postgres-data-v17，并保持账号、密码和数据库名一致。
nano .env
# 创建全新的 PG17 卷，不覆盖原 PG16 卷。
docker compose up -d --wait --wait-timeout 300 postgres
# 先恢复向量与知识数据，再启动 FastAPI，避免空库初始化与恢复同时执行。
docker compose exec -T postgres sh -c \
  'exec psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < /var/backups/meme/postgres-before-pg17.sql
# 恢复成功后更新并验收完整站点。
bash ./deploy.sh
```

若只有旧卷、旧容器已删除，或备份恢复失败，应保留旧卷并停止升级，先完成独立的 PG16 导出恢复。不要通过删除旧卷来让版本检查通过。

## 12. GitHub Actions 手动更新

首次部署并验收完成后，可配置 Actions，后续由工作流调用相同部署脚本：

| Secret | 该服务器的设置 |
|---|---|
| `DEPLOY_HOST` | `8.148.12.121` |
| `DEPLOY_USER` | 实际 SSH 账号，例如 `root` |
| `DEPLOY_SSH_PORT` | `22`，非默认 SSH 端口则填写实际端口 |
| `DEPLOY_PATH` | `/opt/Just_because_meme_understands_you` |
| `DEPLOY_SSH_PRIVATE_KEY` | 专门供 Actions 登录该服务器的 SSH 私钥完整内容 |
| `DEPLOY_SSH_KNOWN_HOSTS` | 核对主机指纹后记录的该服务器 known_hosts 内容 |

对应公钥放进部署用户的 `~/.ssh/authorized_keys`，私钥只保存到 GitHub Secret。`ssh-keyscan` 得到的记录必须与阿里云终端或可信渠道提供的主机指纹核对。不要只扫描后就默认信任。

工作流必须存在于 GitHub 默认分支。先确认“项目质量检查”通过，再到 **Actions → Docker Compose 部署 → Run workflow → ref=main** 手动运行。工作流最多等待 90 分钟，服务器仓库需保持干净；`.env` 位于忽略列表中。工作流不会覆盖服务器 `.env`，也不会自动搬运电脑里的数据。

Actions 的 Runner 也必须能连到服务器 SSH 端口。第 1 步若只放行了你的电脑 IP，GitHub 托管 Runner 会被拦截；需按实际 Runner 出口配置安全组，或使用固定出口的 Runner / 受控网络连接。

## 13. 域名与 HTTPS

当前 Compose 提供 HTTP，适合先验证链路。正式对外使用时，为域名配置 HTTPS，在 TLS 入口正确传递 `X-Forwarded-Proto=https`；调整 `CORS_ALLOWED_ORIGINS`、OAuth 两个回调地址以及 OSS CORS 为 HTTPS 域名，再设置 `JWT_COOKIE_SECURE=true`、`HSTS_ENABLED=true`。本部署没有生成证书，也没有自动配置 443 监听，仅改环境变量不会启用 HTTPS。

当前配置按 Nginx 直接作为公网入口设计。在同一 Nginx 上终止 TLS 时，`$scheme` 会成为 `https`；若另放一个 HTTPS 代理，应同时配置可信代理的真实 IP 和协议来源，避免内层 Nginx 将 HTTPS 来源重新覆盖成 HTTP。

## 14. 常见问题

| 现象 | 优先检查 |
|---|---|
| 镜像拉取超时 | Docker Hub、基础镜像下载网络及自己的阿里云镜像加速配置 |
| 构建出现 Killed / 退出 137 | Swap、服务器物理内存；已串行构建，不要再并行运行两个部署 |
| MySQL unhealthy | `docker compose logs --tail=150 mysql`，磁盘、数据目录和旧账号密码 |
| PostgreSQL 报 incompatible | 是否用 PG17 直接打开 PG16 卷，按第 11 节升级 |
| MemeAgent unhealthy | 向量库连通、维度、初始化日志；不能只看 HTTP 200 |
| 后端失败 | 最早的 Caused by、Flyway migration / checksum、Redis 密码、引导邮箱是否已注册 |
| Redis OOM 错误 | 数据上限 80 MiB 的 noeviction 保护已触发；扩容并提高 Redis 内存，不要直接清空登录与任务状态 |
| 页面正常，AI 搜索失败 | 模型 Key、模型名、API 地址、网络出口及向量回填情况 |
| 上传 403 / CORS | OSS Bucket CORS、直传地址、密钥权限与公开图片访问地址 |
| GitHub Actions 拒绝更新 | `git status --short`；备份、手工改动不应放进生产仓库，脚本通过 Bash 执行 |
