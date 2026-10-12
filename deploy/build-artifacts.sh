#!/usr/bin/env bash
set -Eeuo pipefail

# 兼容旧脚本名称；现在 Dockerfile 会在镜像内直接构建所有服务。
DEPLOY_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$DEPLOY_DIR"

# 首次执行时复制配置模板，提醒填写密钥后重新运行。
if [[ ! -f .env ]]; then
  cp env.production.example .env
  chmod 600 .env
  echo "已生成 deploy/.env；填写密钥后运行 ./deploy.sh 启动全部服务。"
  exit 1
fi

# 安静验证 Compose 配置后构建所有运行镜像。
docker compose config --quiet
docker compose build
