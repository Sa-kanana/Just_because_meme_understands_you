#!/usr/bin/env bash
set -Eeuo pipefail

# 解析脚本所在目录，使从任意当前工作目录启动时都能找到 Compose 文件。
DEPLOY_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$DEPLOY_DIR"

# 任何阶段失败立即停止；不关闭已有服务，也不删除数据库数据卷。
trap 'echo "部署未完成。请检查上方错误；已有服务和数据卷未被脚本主动删除。" >&2' ERR

# 首次启动时生成本地密钥配置模板，不把占位密钥直接部署。
if [[ ! -f .env ]]; then
  cp env.production.example .env
  chmod 600 .env
  echo "已生成 deploy/.env。请先填写数据库、OSS、JWT 和模型密钥，再重新运行 bash ./deploy.sh。"
  exit 1
fi

# 只解析普通 KEY=VALUE 配置，不 source 密钥文件，避免执行其中的 Shell 内容。
read_env() {
  awk -v key="$1" '
    function trim(text) { gsub(/^[ \t]+|[ \t\r]+$/, "", text); return text }
    /^[ \t]*(#|$)/ { next }
    {
      separator = index($0, "=")
      if (separator == 0 || trim(substr($0, 1, separator - 1)) != key) next
      value = trim(substr($0, separator + 1))
      quote = substr(value, 1, 1)
      if (quote == "\"" || quote == sprintf("%c", 39)) {
        value = substr(value, 2)
        closing_quote = index(value, quote)
        if (closing_quote > 0) value = substr(value, 1, closing_quote - 1)
      } else {
        sub(/[ \t]+#.*/, "", value)
      }
      result = trim(value)
    }
    END { print result }
  ' .env
}

# 同一个字段只允许配置一次，避免前后两处值不一致导致排查困难；只打印字段名。
duplicate_env="$(awk -F= '/^[ \t]*[A-Za-z_][A-Za-z0-9_]*[ \t]*=/ { key=$1; gsub(/[ \t]/, "", key); if (++seen[key] == 2) print key }' .env)"
if [[ -n "$duplicate_env" ]]; then
  printf 'deploy/.env 存在重复配置字段：\n%s\n' "$duplicate_env" >&2
  exit 1
fi

# 必填项及其用途；真实值不会打印到终端。
required_env=(
  MYSQL_ROOT_PASSWORD       # MySQL root 管理密码。
  MYSQL_APP_PASSWORD        # Spring Boot 的 MySQL 专用账号密码。
  REDIS_PASSWORD            # Redis 访问密码。
  POSTGRES_PASSWORD         # MemeAgent PostgreSQL 密码。
  MEME_AGENT_API_KEY        # Java 与 FastAPI 共享的内部密钥。
  JWT_SECRET                # JWT 签名密钥。
  OSS_ACCESS_KEY_ID         # OSS 访问密钥 ID。
  OSS_ACCESS_KEY_SECRET     # OSS 访问密钥 Secret。
  OSS_BUCKET_NAME           # OSS 文件存储 Bucket。
  OPENAI_API_KEY            # MemeAgent 的模型服务 API 密钥。
)

# 检查必填配置是否缺失或仍保留示例占位符。
missing_env=()
for env_name in "${required_env[@]}"; do
  env_value="$(read_env "$env_name")"
  if [[ -z "$env_value" || "$env_value" == *REPLACE_ME* || "$env_value" == *YOUR_* ]]; then
    missing_env+=("$env_name")
  fi
done

# 有必填项未配置时停止，避免启动一个看似成功但核心功能不可用的站点。
if (( ${#missing_env[@]} > 0 )); then
  printf '请先在 deploy/.env 配置以下字段：%s\n' "${missing_env[*]}" >&2
  exit 1
fi

# JWT 需要足够长的签名密钥；只检查长度，不显示密钥。
jwt_secret="$(read_env JWT_SECRET)"
if (( ${#jwt_secret} < 32 )); then
  echo 'JWT_SECRET 至少需要 32 字节，建议使用 openssl rand -hex 32。' >&2
  exit 1
fi

# PostgreSQL 密码直接拼接进 URL，因此拒绝需要百分号编码的特殊字符。
postgres_password="$(read_env POSTGRES_PASSWORD)"
if [[ ! "$postgres_password" =~ ^[A-Za-z0-9._~-]+$ ]]; then
  echo 'POSTGRES_PASSWORD 必须使用 URL 安全字符，建议使用 openssl rand -hex 32。' >&2
  exit 1
fi

# 仅允许 Docker Compose 配置校验和镜像构建通过后再启动容器。
if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 Docker。请先在服务器安装 Docker Engine 和 Docker Compose v2。" >&2
  exit 1
fi

# 同时检查 Compose 插件与 Docker 守护进程，权限不足时在构建前返回明确错误。
if ! docker compose version >/dev/null 2>&1; then
  echo 'Docker Compose v2 插件不可用，请安装 docker-compose-plugin。' >&2
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo '无法连接 Docker 守护进程。请检查 systemctl status docker，以及当前用户的 Docker 权限。' >&2
  exit 1
fi

# 保护服务器上的密钥文件权限，配置校验采用 quiet 模式避免打印环境变量。
chmod 600 .env
http_port="$(read_env HTTP_PORT)"
http_port="${http_port:-80}"

# 健康检查等待秒数，可在 .env 中调整；不包含镜像下载和构建所需时间。
wait_timeout="$(read_env DEPLOY_WAIT_TIMEOUT_SECONDS)"
wait_timeout="${wait_timeout:-900}"
if [[ ! "$wait_timeout" =~ ^[1-9][0-9]*$ ]]; then
  echo 'DEPLOY_WAIT_TIMEOUT_SECONDS 必须为正整数秒数。' >&2
  exit 1
fi

# 固定项目名和配置路径；串行调度避免小内存服务器同时拉取、构建多个服务。
compose=(docker compose --project-name just-because-meme --env-file "$DEPLOY_DIR/.env" --file "$DEPLOY_DIR/docker-compose.yml" --parallel 1)
"${compose[@]}" config --quiet

# 先拉取数据库运行镜像；后续只在所有镜像构建成功后更新现有服务。
"${compose[@]}" pull mysql redis postgres

# PostgreSQL 17 才支持应用使用的 transaction_timeout；旧数据卷禁止直接跨大版本启动。
postgres_volume="$(read_env POSTGRES_VOLUME_NAME)"
postgres_volume="${postgres_volume:-just-because-meme_postgres-data}"
if docker volume inspect "$postgres_volume" >/dev/null 2>&1; then
  postgres_major="$(docker run --rm --network none --read-only \
    --mount "type=volume,src=$postgres_volume,dst=/existing-data,readonly" \
    --entrypoint sh pgvector/pgvector:pg17 \
    -c 'if [ -f /existing-data/PG_VERSION ]; then cat /existing-data/PG_VERSION; fi')"
  if [[ -n "$postgres_major" && "$postgres_major" != 17 ]]; then
    echo '检测到非 PostgreSQL 17 数据卷。请先按 deploy/README.md 导出并升级，不能直接更换镜像。' >&2
    exit 1
  fi
fi

# 逐个构建镜像，任意构建失败均不执行容器更新；前端两个构建阶段也在 Dockerfile 中串行。
"${compose[@]}" build backend
"${compose[@]}" build meme-agent
"${compose[@]}" build nginx

# 在独立的一次性容器中验证 Nginx 语法，不映射宿主机端口，也不启动业务依赖。
"${compose[@]}" run --rm --no-deps nginx nginx -t

# 启动已完成构建的服务，等待真实健康检查；不使用 --build 再次触发并行构建。
"${compose[@]}" up --detach --no-build --remove-orphans --wait --wait-timeout "$wait_timeout"

# 展示容器运行状态，不输出任何密钥值。
"${compose[@]}" ps
echo "部署完成：访问 http://服务器地址:${http_port}/；管理端路径为 /admin/。"
