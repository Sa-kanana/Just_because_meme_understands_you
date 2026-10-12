# Meme 向量库增长治理设计

## 目标

控制 `meme_vector_chunk` 的非预期膨胀，确保 Java 梗库与 pgvector 中的已发布梗可以由管理员按需双向校准。梗向量不按创建时间或更新时间自动过期；全量校准不按固定周期自动运行。

## 当前问题

1. `meme_vector_chunk` 没有自动保留期限；正常更新会事务性替换旧分块，相同 `content_hash` 会跳过 embedding，删除/下架事件会删除对应向量。
2. `MAX_CHUNKS_PER_MEME = 6` 当前用于检索候选数估算，不限制入库分块数。
3. 入库将标题和标签前缀重复写入每个正文分块。标签限制了数量但未限制单项或累计长度；当前 chunker 在前缀过长时会将正文分块宽度压到 1 个字符，最多可为一条 5000 字介绍创建数千个重复前缀向量。
4. Java outbox 负责正常索引事件，但历史漏投、最终失败或人工变更可能使 Java 与 pgvector 状态不一致；目前管理端没有全量对账与修复入口。

## 设计

### 1. 入库内容与分块上限

- 保留标题最多 200 字、介绍最多 5000 字、标签最多 20 项。
- 每个标签最多 64 字，标签去空白后的累计长度最多 240 字；超限由请求校验拒绝，不静默截断。
- 将 `MAX_CHUNKS_PER_MEME = 6` 放入共享限制模块，让检索候选计算与入库分块共用同一值。
- chunker 在调用 embedding 前确保输出不超过 6 块。第一块保留标题与标签，后续块重复短标题前缀，不再重复标签；保持默认每块不超过 1200 字。
- 若未来修改分块规则导致合法字段仍产生超过 6 块，明确拒绝该文档并记录梗 ID、实际块数和上限，不生成 embedding、不写入半成品。

### 2. 管理端手动双向校准

全量校准由管理员在 AI 运维控制台主动启动，不配置每日或其他固定周期的全量清理任务。Java 的既有 outbox 定时投递器继续负责发送已持久化的单梗索引事件；它不是全量校准的触发器。

校准分成两个可恢复阶段：

1. **Java → pgvector 补齐与更新**：Java 按 `meme_id` 游标分页读取已发布梗及其标签，每页最多 50 条，将梗索引 upsert 事件写入现有 outbox。现有 outbox relay 仍以 Java 当前业务状态为准调用 MemeAgent。Python 对 `content_hash` 未变化的文档跳过 embedding；缺失或内容变化的文档按现有入库逻辑生成向量。
2. **pgvector → Java 清理无效向量**：MemeAgent 通过仅供内部写密钥访问的游标接口，按 `meme_id` 分页返回向量库中存在的梗 ID，不返回正文或 embedding。Java 批量查询每页 ID 的已发布状态；不存在或未发布的梗写入现有 `MEME_INDEX_REMOVE` outbox 事件。事件 relay 在真正删除前重新读取 Java 当前状态；若梗已重新发布则执行 upsert，避免并发重发时误删有效向量。

任务与管理方式：

- 管理端在 AI 运维页提供“梗向量库校准”操作，展示最近任务状态、阶段、已扫描梗数、已写入 outbox 的补齐/更新事件数、待清理事件数及失败阶段。点击前提示：任务可能为缺失或已变更梗调用 embedding；相同内容会跳过。
- `POST /admin/v1/ai/vector-index/reconciliation-jobs` 由 `ADMIN` 或 `SUPER_ADMIN` 启动校准并返回任务 ID；`GET /admin/v1/ai/vector-index/reconciliation-jobs/{jobId}` 查询任务进度。普通用户和浏览器侧 MemeAgent 接口均无权访问内部向量 ID 分页 API。
- 只允许一个校准任务处于活动状态，防止管理员重复点击造成扫描和 embedding 并发。重复启动返回当前活动任务；完成后管理员可再次手动启动。
- 校准任务状态、阶段、两侧游标、计数、创建人和时间写入 Java MySQL 任务表，以便刷新页面后查看进度，并在 Java 重启后从最后确认游标继续。后台 worker 只处理管理员创建的校准任务；它可以轮询并恢复未完成任务，但不会自行创建或定时启动全量校准。新建表的每一列都写中文 `COMMENT`；迁移字段如复制其他字段，逐项说明来源和目标。本设计不需要复制字段。
- 处理顺序为先完整扫描 Java 已发布梗，再扫描向量库 ID。每页的 outbox 事件成功持久化后才前进游标；外部调用失败不会丢失已写入 outbox 的事件。任务状态将批量扫描与 outbox 排队明确标为“已排队”，不会误报成“向量已写入”；单梗最终投递仍由既有 outbox 状态和日志追踪。
- 任务失败时保留游标和失败阶段。管理员可在控制台继续该任务；若阶段数据不再可用，可显式重新启动一次全量校准。分页、outbox 和 Python upsert 均按梗 ID 幂等，重跑不重复生成未变更内容的 embedding。
- 删除只通过现有 outbox 与 Python 删除路径执行；Java 启动校准时不直接删除向量。不会按年龄清除仍然有效的旧梗。

### 3. 容量观测与 PostgreSQL 清理

新增只读 SQL 运维脚本，展示梗数、chunk 数、每梗最大/平均 chunk 数、表与索引总大小、HNSW 索引大小及 dead tuple/最近 autovacuum 信息。PostgreSQL autovacuum 负责回收删除产生的 dead tuple 以供复用；不自动执行会长时间锁表的 `VACUUM FULL`。只有指标显示 HNSW 膨胀时，运维手动评估 `REINDEX INDEX CONCURRENTLY`。

## API 与兼容性

- 现有 `/ingest`、`/ingest/delete`、Java outbox 语义及单条同步/审核灌库入口保持不变。
- 新增管理端校准启动/状态 API、新的 Python 内部梗 ID 游标 API、Java 任务表及可恢复批处理；不新增公开的浏览器直连 Python 能力。
- 新 Python 接口使用 `WriteAuth`，只返回有序梗 ID 和游标，不传业务正文、标签或 embedding。
- Java 通过既有 outbox 触发 upsert/remove；校准阶段失败与 outbox 投递失败分别记录，日志只记录任务 ID、阶段、梗 ID 范围、计数、耗时和安全错误类型，不记录正文、标签或 embedding。
- 不改 `meme_vector_chunk` 数据模型；不新增 Python 依赖。如实现时新增 SQL DDL，每个列均补中文 `COMMENT`。
- 尊重当前工作区的未提交修改；仅新增明确的批量校准管理流程，不恢复旧的手动 backfill API 名称或调用语义。

## 验收条件

1. 超长单标签、累计超长标签在请求校验阶段失败。
2. 接近字段上限的有效梗最多生成 6 个 chunk；chunker 永远不会因长前缀将正文宽度退化为 1 字。
3. 超过分块上限时 embedding 调用次数为 0，向量表不产生部分写入。
4. 校准只能由管理员显式启动；不存在定时全量校准配置或任务。
5. Java 已发布梗按稳定游标分页写入 outbox；未变化内容不会再次调用 embedding；任务重启后从确认游标继续。
6. Python 向量 ID 分页稳定且无重复/漏页；只为不存在或非公开的 ID 生成删除事件。
7. 校准扫描后若梗重新发布，outbox relay 重新读取业务状态并保留/重建向量，不误删新发布梗。
8. 活动任务不能重复启动；页面刷新后可查询同一任务及进度。任务失败保留进度并可由管理员继续。
9. 容量脚本可在当前 PostgreSQL/pgvector 环境执行并输出表、索引和 vacuum 统计。
10. Python 与 Java 相关单测通过；不执行真实向量删除或 embedding API 调用。

## 不包含

- 对有效梗按年龄做 TTL。
- 定时全量校准或自动清理有效向量。
- 对有效梗进行自动摘要或静默截断。
- 改变管理员知识库保留、清理或检查点 TTL。
- 让管理端任务绕过 Java outbox 直接向 Python 发起每条梗的 embedding 请求。
