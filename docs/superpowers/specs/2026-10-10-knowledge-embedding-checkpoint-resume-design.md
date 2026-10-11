# 知识向量分批检查点与失败提醒设计

日期：2026-10-10

状态：待用户审阅

范围：MemeAgent Python、PostgreSQL/pgvector、Java 知识 outbox、管理员前端

## 背景

管理员知识入库在 Python 中先为所有片段生成 embedding，全部成功后才开启数据库事务写入 `knowledge_vector_chunk`。embedding 在中途失败时，先前成功返回的批次没有持久化；Java outbox 重试会重新调用 Python，因此可能重复计算已经成功的片段。

Java outbox 对事件最多尝试 12 次，达到上限后把知识文档状态置为 `FAILED` 并保存错误。管理员知识页面在当前视图轮询到 `FAILED` 时已有错误 toast，失败行也会显示错误 tooltip；页面未打开期间发生的失败没有主动提醒。

## 目标

- 供应商批次成功返回且向量校验通过后，立即持久化该批次的检查点。
- 同一正文、片段和 embedding 配置再次入库时复用有效检查点，只请求缺失片段的向量。
- 全部片段具备有效向量后，在一个 PostgreSQL 事务中替换正式索引、更新 `knowledge_doc_state` 并清理本次检查点。
- Java outbox 重试耗尽后，管理员能看到失败提醒、已完成片段数、总片段数和可安全展示的错误摘要。
- 文档删除时清除正式向量和对应文档的所有检查点。
- 检查点最多保留 7 天，清理任务在多个 Python worker 下也只能并发执行一次。

## 不在本次范围

- 不改变知识切块、检索排序、HNSW 参数或 embedding 维度。
- 不将向量或大文本放入 Java MySQL outbox 事件。
- 不在供应商请求没有成功返回向量时声称这些向量已被保存。供应商内部已计算但未返回的结果不可恢复。
- 不为 embedding 失败改用确定性本地向量或其他模型降级。
- 不改变知识入库 API 的成功响应结构、Java outbox 重试次数和退避规则。

## 已确认的设计决策

- 使用 PostgreSQL 持久化 embedding 检查点。进程内缓存不能覆盖 Python 重启；在 MySQL outbox 中传递向量会放大业务事件并混合存储职责。
- 以“供应商成功返回、数量和维度均校验通过、检查点事务提交”为一个已完成批次。当前供应商每批最多 25 条，检查点按该批次边界写入。
- 检查点指纹由知识文档 ID、标准化正文 SHA-256、片段序号与片段 SHA-256、embedding 配置指纹共同确定。配置指纹包括 endpoint 身份、模型名和维度，不包含 API 密钥。相同正文重新灌库产生新 revision 时可安全复用匹配向量。
- 检查点按完整片段文本校验，不以序号单独判定可复用。模型、endpoint、维度、正文或片段不匹配时重新生成对应向量。
- Python 每处理完一批便提交检查点。请求失败时保留有效检查点；Java 现有 outbox 将使用同一事件重试。人工重新灌库产生新 revision 时，只复用仍匹配正文和模型指纹的检查点。
- 只有所有片段向量齐备后，才在事务中更新正式向量数据。现有 READY 向量在新索引完整构建前继续可检索；失败的部分数据只留在检查点表，不参与检索。
- 失败通知只针对 Java outbox 重试耗尽后的最终 `FAILED`。临时尝试失败仍由 Java 自动重试，不弹出用户通知。
- 管理端在正在查看知识页时沿用任务轮询，并在终态失败时显示一次 toast；进入知识页时检查未提醒的失败 task，以摘要 toast 提醒。通过 `indexTaskId` 在当前浏览器会话中去重；失败原因和进度保留在行状态 tooltip 中。
- 检查点保留 7 天，超过期限后由 Python 后台清理任务删除。文档删除会立即删除其所有检查点，不等待 TTL。
- PostgreSQL 启动建表代码与手动初始化 SQL 同步维护。新增 SQL 表的每一列必须有中文 `COMMENT`。

## 数据库设计

新增 `knowledge_embedding_checkpoint` 表。向量列维度与现有 `vector_embedding_dim` 配置一致；表不加入检索查询，不创建 HNSW 索引。建议列如下：

| 列 | 类型 | 说明 / PostgreSQL COMMENT |
| --- | --- | --- |
| `doc_id` | `VARCHAR(64) NOT NULL` | 管理员知识文档 ID |
| `content_sha256` | `CHAR(64) NOT NULL` | 标准化知识正文 SHA-256 |
| `embedding_fingerprint` | `CHAR(64) NOT NULL` | endpoint 身份、模型名和维度的 SHA-256 指纹 |
| `chunk_index` | `INT NOT NULL` | 文档内片段序号 |
| `chunk_sha256` | `CHAR(64) NOT NULL` | 当前片段文本 SHA-256，用于防止错位复用 |
| `embedding` | `vector(<configured_dim>) NOT NULL` | 已校验并持久化的片段向量 |
| `created_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 检查点首次持久化时间，用于 TTL 清理 |
| `updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT NOW()` | 检查点最后确认时间 |

主键为 `(doc_id, content_sha256, embedding_fingerprint, chunk_index)`。每个查询结果仍须对比 `chunk_sha256`；不相等时视为缺失并覆盖该检查点。索引至少支持按文档与正文指纹快速加载，以及按 `created_at` 清理过期数据。DDL 中对以上每列逐项执行 `COMMENT ON COLUMN`。

## 入库流程

1. Python 对正文、切块结果和 embedding 配置计算指纹，并保留现有 revision 与状态校验。
2. 对所有 chunk_index 加载精确匹配的检查点。只有文档 ID、正文指纹、配置指纹、片段序号和片段哈希都匹配的向量可复用。
3. 将缺失片段按最多 25 条分组。每组调用 embedding API 后检查返回数量、向量维度及数值有效性，然后在短事务中批量 upsert 检查点。不得在网络请求期间持有数据库事务或行锁。
4. 若 API、维度校验或检查点提交失败，记录已持久化数量和总片段数并使请求失败；不清理此前成功批次的检查点。Java 按现有 outbox 策略重试后从缺失片段继续。
5. 所有向量齐备后，在正式写入事务中取得文档级 advisory lock，再锁定并校验 `knowledge_doc_state`。若更新版本或删除墓碑已生效，则不发布旧请求的向量。
6. 在该事务内重读并核对全部检查点，删除该文档旧的正式向量、写入完整新片段、更新 `knowledge_doc_state` 为 `READY`，并删除此次正文和配置指纹对应的检查点。
7. 事务提交使新向量整体可见；任何事务失败都会保留此前 READY 向量和检查点，供后续重试使用。

空切块文档沿用当前空批次行为：不调用模型，按原子发布路径清理旧片段并标记 READY；不得留下无意义检查点。

## 并发、版本和删除

- 检查点的唯一键保证同一文档、正文、配置和序号只有一条向量记录。并发重试可能重复调用模型，但写入幂等；不使用跨网络请求的长锁。
- 每次检查点写入及正式发布都必须校验版本围栏。`knowledge_doc_state` 已有比请求 revision 更新的状态，或为不允许该 upsert 的 DELETED 墓碑时，旧任务不得持久化新检查点或发布向量。
- 删除事务取得文档 advisory lock，在写入 DELETED 墓碑的同一事务中删除 `knowledge_vector_chunk` 和 `knowledge_embedding_checkpoint` 的该文档所有行。晚到的旧 worker 经过版本围栏后不能重新创建被删检查点。
- 新 revision 若正文未变且 embedding 配置相同，可以复用检查点；正文或配置变更后使用不同指纹，不会混用旧模型向量。
- 同一 revision 对应不同正文的现有冲突检查继续有效。

## 失败信息与管理员提醒

- Python 对 embedding 批次失败生成结构化、长度受限的错误详情，包括 `completed_chunks`、`total_chunks`、错误阶段和已净化的错误摘要。不得在用户可见错误中包含 API 密钥、原始知识正文或完整供应商响应。
- Python HTTP 错误响应携带上述详情。Java 知识 outbox handler 读取响应并净化进 `index_error`；错误详情保留现有字段长度上限。错误响应不可解析时使用安全通用消息，同时完整异常只进入服务端日志。
- 中间重试时 Java 保持 `PENDING` 状态，不向管理端显示终态失败。达到 `MAX_ATTEMPTS` 后，outbox 将事件标为 DEAD、知识文档设为 `FAILED`，并保存最后一次错误详情。此时知识页显示失败状态和进度 tooltip。
- 失败发生时若管理员仍在知识页，现有轮询检测到 FAILED 后弹出包含进度的错误 toast。
- 管理员以后打开知识页时，前端检查 `FAILED` 行；对当前浏览器会话中尚未提示的 `indexTaskId` 显示一次汇总提醒。新的 task ID 会重新提醒；旧任务不会在每次列表刷新时重复弹窗。
- 用户手动重新灌库时仍创建新 revision 与 outbox task。若正文和 embedding 配置相同，Python 可重用未过期检查点；最终错误状态只属于旧 task。

## 检查点清理

- Python 后台任务每日执行一次 `DELETE ... WHERE created_at < NOW() - INTERVAL '7 days'`。
- 多 worker 通过 PostgreSQL advisory lock 选出单一清理者；清理异常只写服务日志，不影响知识查询、上传或删除请求。
- 每次知识删除会立即清除该文档全部检查点；成功发布会立即清除本次使用的检查点。
- 新增 TTL 配置字段若实现为可配置值，需附清晰中文注释并约束正数。默认值为 7 天。

## 兼容与运维

- 不改变 Java outbox payload、Python 入库 endpoint、正常响应 schema、READY/DELETED 状态契约或知识检索 SQL。
- Postgres schema 初始化通过 `app/retrieval/db.py` 建表和加注释；同步更新 `scripts/init_pgvector.sql`，保证新环境与手工初始化一致。
- 现存 `knowledge_vector_chunk` 和 `knowledge_doc_state` 数据不迁移。新表为空即可上线；正在处理中旧任务首次重试会从零建立检查点。
- 若数据库不可用，embedding 不应继续批量消耗模型费用而无法保存进度：每个批次写 checkpoint 失败后立即终止并交由 Java outbox 重试。
- 检查点表存储向量而不存原始 chunk 正文。正文由 Java 当前 outbox payload 重新切块，片段哈希用于校验是否可复用。

## 验收场景

1. 文档超过一个供应商批次时，前一批成功并提交检查点，后一批失败；重试只为剩余片段调用模型。
2. 在成功批次后重启 Python worker，再重放同一事件；已保存向量仍可复用。
3. 模型名、endpoint 身份、维度、正文或片段哈希任一变化时，对应检查点不可复用。
4. 所有检查点齐备时只整体发布一次；检索不会读到 staging 表中的部分结果。
5. 正式发布事务失败后，旧 READY 向量仍可检索，已保存检查点仍存在。
6. 删除与较旧 upsert 并发时，最终状态为 DELETED，正式向量和检查点均无残留。
7. 过期检查点可由单 worker 清理；其他 worker 不重复执行清理事务。
8. outbox 的非终态失败不产生管理员 toast；最终 FAILED 保存进度，当前页面和重新打开的知识页都能提醒，且同一 task 在会话内只提示一次。

## 风险与约束

- PostgreSQL 中暂存向量占用额外存储，TTL、成功发布清理和删除清理共同限制其生命周期。
- 检查点仅复用已完整返回并成功提交到 PostgreSQL 的批次。若供应商完成计算后连接超时、结果未送达客户端，不能恢复服务端未交付的向量。
- 当前 provider 的每批 25 条限制继续适用；未来变更该上限时，检查点仍以实际成功 API 批次为边界。
- endpoint 与模型名组成的配置指纹不能识别供应商在不变模型别名下悄悄替换权重；检查点只保留 7 天以缩小此风险窗口。
