@echo off
setlocal EnableExtensions

rem 兼容旧脚本名称；Dockerfile 会在 Docker Compose 构建镜像时构建各服务。
cd /d "%~dp0"

rem 首次执行时复制配置模板，并提醒用户填写密钥。
if not exist .env (
    copy /Y env.production.example .env >nul
    echo 已生成 deploy\.env；填写密钥后运行 deploy.sh 启动全部服务。
    exit /b 1
)

rem 安静验证 Compose 配置，再构建所有容器镜像。
docker compose config --quiet
if errorlevel 1 exit /b 1
docker compose build
if errorlevel 1 exit /b 1
endlocal
