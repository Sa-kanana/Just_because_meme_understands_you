# MemeAgent

LangChain + FastAPI 内部智能体：pgvector 检索、异步灌库、SSE 流式 AI 搜索。  
**无状态**；会话与业务真相在 Java + MySQL。

架构详见 `.cursor/skills/meme-agent/`。

## 快速开始

### 1. Conda 环境

```bash
cd MemeAgent
conda env create -f environment.yml
conda activate memeagent
cp .env.example .env
# 编辑 .env：INTERNAL_API_KEY、VECTOR_DATABASE_URL、OPENAI_API_KEY
```

### 2. PostgreSQL + pgvector

```bash
# 示例：Docker
docker run -d --name memeagent-pg \
  -e POSTGRES_USER=memeagent \
  -e POSTGRES_PASSWORD=memeagent \
  -e POSTGRES_DB=memeagent_vector \
  -p 5433:5432 \
  pgvector/pgvector:pg16

psql "postgresql://memeagent:memeagent@127.0.0.1:5433/memeagent_vector" \
  -f scripts/init_pgvector.sql
```

### 3. 启动服务

```bash
conda activate memeagent
# 方式一（推荐）
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
# 方式二
python -m app.main
```

> `VECTOR_DATABASE_URL` 必须带账号密码，例如 `postgresql://postgres:密码@192.168.x.x:5432/postgres`。
> 若写成 `postgresql://host:5432/db`（无用户），Windows 会用系统用户名登录并报 `password authentication failed`。

- 健康检查：`GET http://127.0.0.1:8000/health`
- OpenAPI：`http://127.0.0.1:8000/docs`（仅内网）

### 4. 与 Java 联调

Java 配置 `meme.agent.base-url=http://127.0.0.1:8000`，`meme.agent.api-key` 与 `INTERNAL_API_KEY` 一致。

## API（内网）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 探活 |
| POST | `/ingest` | 异步灌库（需 `X-Internal-Api-Key`） |
| POST | `/ingest/delete` | 按 meme_id 删向量 |
| POST | `/stream` | AI 搜索 SSE 流 |

## 测试

```bash
pytest -q
```

## 目录

```
app/
  api/          # HTTP 路由
  agents/       # LangChain 编排
  core/         # 配置、鉴权、生命周期
  ingest/       # 切块与异步灌库
  retrieval/    # pgvector
  schemas/      # Pydantic 契约
  eval/         # LangSmith 评估
scripts/        # SQL 初始化
tests/
```
