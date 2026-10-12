# 脚本目录

本目录放置 PostgreSQL 初始化 SQL、AI 压测和 MemeAgent 采集维护脚本。脚本生成的临时结果统一写入 `output/`；该目录已加入 `.gitignore`，不会混入源码提交。

## 脚本用途

| 文件 | 用途 | 副作用 |
| --- | --- | --- |
| `benchmark_ai_stream.py` | 测量 SSE 首 token 延迟、总耗时、错误数和吞吐量 | 会调用指定 AI 接口；调用模型可能产生费用 |
| `backfill_crawl_intros.py` | 抓取固定梗出处并生成简介回填 JSON，供人工复核 | 会调用 Firecrawl 和摘要模型；不直接写业务数据库 |
| `probe_bilibili.py` | 检查 Firecrawl 对 B 站页面和搜索的响应 | 会调用 Firecrawl |
| `smoke_crawl.py` | 检查梗词抽取和两条采集路径 | 会调用采集服务 |
| `smoke_interact_crawl.py` | 检查投稿页交互抓取及后续采集路径 | 会调用 Firecrawl 和采集服务 |
| `smoke_crawl_pipeline.py` | 检查完整采集链路并校验候选内容 | 会调用采集服务 |
| `verify_crawl_e2e.py` | 校验端到端采集候选的来源和结构 | 会调用采集服务 |
| `init_pgvector.sql` | 初始化 pgvector 扩展、向量表和索引 | 会在目标 PostgreSQL 创建或更新结构 |
| `inspect_meme_vector_capacity.sql` | 查看梗向量表、索引、HNSW 和自动清理统计 | 只读查询 PostgreSQL |

## 运行方式

从 `MemeAgent` 目录执行脚本，例如：

```powershell
python scripts/smoke_crawl_pipeline.py
python scripts/verify_crawl_e2e.py
python scripts/benchmark_ai_stream.py --requests 1 --concurrency 1
```

采集脚本使用 `.env` 中的 Firecrawl 和模型配置。压测脚本默认访问本机 `http://127.0.0.1:8000/stream`，可用 `--url`、`--body-file`、`--api-key` 和 `--bearer-token` 覆盖请求配置；压测脚本支持 `--help` 查看参数。

## 梗向量容量诊断

从 `MemeAgent` 目录使用配置好的 PostgreSQL 连接运行只读脚本：

```powershell
psql $env:VECTOR_DATABASE_URL -v ON_ERROR_STOP=1 -f scripts/inspect_meme_vector_capacity.sql
```

脚本输出梗记录和向量片段数量、单梗最大/平均分块数、表与索引占用、HNSW 索引大小、估算死元组数量，以及最近自动清理时间。死元组统计属于估算值；PostgreSQL autovacuum 通常会回收并复用这些页面空间，不会自动缩小操作系统看到的文件大小。若容量观察确认 HNSW 索引膨胀，可由管理员单独评估 `REINDEX INDEX CONCURRENTLY`；本脚本不修改数据、不重建索引，也不会调用 Embedding 服务。

## 结果文件

- `output/`：脚本每次运行生成的临时结果，已忽略，不纳入版本控制。
- 历史诊断和回填结果不保存在仓库中；需要时运行对应脚本重新生成。
