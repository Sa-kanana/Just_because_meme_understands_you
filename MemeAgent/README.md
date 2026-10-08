# MemeAgent

LangChain + FastAPI 内部智能体：pgvector 检索、异步灌库、SSE 流式 AI 搜索。  
**无状态**；会话与业务真相在 Java + MySQL。

架构详见 `.cursor/skills/meme-agent/`。

系统提示由 [`app/agents/prompt_files/search_system_zh.md`](app/agents/prompt_files/search_system_zh.md) 管理。
修改后同步升级 Python `PROMPT_VERSION` 和 Java `AI_ANSWER_CACHE_PIPELINE_VERSION`，重启 MemeAgent；工具输入与检索结果的结构由 Pydantic JSON Schema 约束。

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

首次启动会为梗向量表和运营知识向量表创建余弦距离 HNSW 索引；也可以提前执行 `scripts/init_pgvector.sql`。HNSW 用近似最近邻换取更快检索，适合数据增长后的搜索；小表上 PostgreSQL 仍可能选择精确扫描。向量维度需在 1–2000 之间，并与 embedding 模型输出一致。

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

## AI 搜索吞吐与压测

- 相同查询的 embedding 在单个 worker 内按 TTL 缓存，并发重复请求会合并为一次供应商调用；Agent 工具在单次请求内缓存完整检索结果。缓存是进程本地的，worker 重启或其他 worker 不共享。
- `VECTOR_POOL_MIN_SIZE`、`VECTOR_POOL_MAX_SIZE` 和 `VECTOR_POOL_ACQUIRE_TIMEOUT_SECONDS` 控制每个 worker 的 PostgreSQL 连接池。数据库连接预算按“worker 数 × 最大连接数”预留，另外还要给 Java、管理任务和数据库运维留连接。
- Java 网关的 `MEME_AGENT_MAX_CONNECTIONS` 与 `MEME_AGENT_PENDING_ACQUIRE_MAX_COUNT` 控制 SSE 上游连接和等待队列；应与 MemeAgent worker 数、模型供应商并发限制一起调节。不要只提高连接数而不检查 PostgreSQL 与模型供应商容量。
- 可用内置脚本测量 TTFT、完整 SSE 耗时、p50/p95 和吞吐量。默认只向本机发 1 个请求；高并发会实际调用模型并消耗供应商额度，建议在测试环境逐级增加并发：

```powershell
$env:MEME_AGENT_API_KEY = "你的内部密钥"
python scripts/benchmark_ai_stream.py --requests 20 --concurrency 4 --query "只因是什么梗"
```

也可通过 `--url` 和 `--body-file` 指向 Java `/v1/ai/searches` 测端到端链路，并使用 `--bearer-token` 提供测试账号 JWT。脚本返回的每个请求结果会标明 HTTP 状态、TTFT、token 数与 SSE 错误数。

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
