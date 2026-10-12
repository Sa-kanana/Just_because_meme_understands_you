#!/usr/bin/env bash
set -Eeuo pipefail

# 解析脚本所在目录，使从任意当前工作目录启动时都能找到 Compose 文件。
DEPLOY_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$DEPLOY_DIR"

# 首次启动时生成本地密钥配置模板，不把占位密钥直接部署。
if [[ ! -f .env ]]; then
  cp env.production.example .env
  chmod 600 .env
  echo "已生成 deploy/.env。请先填写数据库、OSS、JWT 和模型密钥，再重新运行 ./deploy.sh。"
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
  env_value="$(awk -F= -v key="$env_name" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' .env)"
  if [[ -z "$env_value" || "$env_value" == *REPLACE_ME* || "$env_value" == *YOUR_* ]]; then
    missing_env+=("$env_name")
  fi
done

# 有必填项未配置时停止，避免启动一个看似成功但核心功能不可用的站点。
if (( ${#missing_env[@]} > 0 )); then
  printf '请先在 deploy/.env 配置以下字段：%s\n' "${missing_env[*]}" >&2
  exit 1
fi

# 仅允许 Docker Compose 配置校验和镜像构建通过后再启动容器。
if ! command -v docker >/dev/null 2>&1; then
  echo "未找到 Docker。请先在服务器安装 Docker Engine 和 Docker Compose v2。" >&2
  exit 1
fi

# 保护服务器上的密钥文件权限，配置校验采用 quiet 模式避免打印环境变量。
chmod 600 .env
http_port="$(awk -F= '$1 == "HTTP_PORT" { sub(/^[^=]*=/, ""); print; exit }' .env)"
http_port="${http_port:-80}"
docker compose config --quiet

# 构建 Java、FastAPI 与两个 Vue 前端镜像，等待所有健康检查通过。
docker compose up --detach --build --remove-orphans --wait --wait-timeout 600

# 展示容器运行状态，不输出任何密钥值。
docker compose ps
echo "部署完成：访问 http://服务器地址:${http_port}/；管理端路径为 /admin/。"
