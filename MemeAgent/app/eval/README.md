# MemeAgent 检索与回答评估

评估集保存在 `golden_queries.json`，目前以固定梗名作为标签，避免不同数据库中的自增 ID 让结果失去可比性。标签和事实词组由项目维护者人工确认；每次修改检索、提示词或模型配置后，使用同一问题集比较指标。

## 管理员知识库检索评估与门槛校准

管理员知识库使用独立的评估集。先把真实用户问题保存到 `app/eval/admin_knowledge_golden_queries.json`，可以暂时只写 `query` 和 `top_k` 来查看候选；人工核对候选后，再给问题补上 `expected_doc_ids` 或 `expected_empty` 标签。正例必须对应当前已发布知识文档，空结果负例用于测量误命中。

```json
[
  {"query": "如何修改已上传的资料？", "top_k": 5},
  {"query": "知识库里没有的某个特定问题", "top_k": 5}
]
```

从 `MemeAgent` 目录第一次运行候选收集：

```bash
python -m app.eval.admin_knowledge_eval app/eval/admin_knowledge_golden_queries.json --output reports/admin-knowledge-candidates.json
```

根据候选文档 ID 和章节人工标注原始问题集：正例填写 `expected_doc_ids`，确认知识库中没有答案的问题填写 `expected_empty: true`。之后用相同命令重新评估。报告包含门槛建议、Precision@K、Recall@K、MRR、空结果准确率及检索耗时。门槛由标注候选校准，不会自动写入 `.env`；复核报告后再设置 `ADMIN_KNOWLEDGE_MIN_SIMILARITY`。没有正例标签时只生成候选报告，不会虚构校准值。

当前未配置门槛时，线上会保留标题、标签和正文完整短语命中，但不会接纳没有校准依据的纯向量候选。Agent 对非问候问题会并发预检索站内梗库和管理员知识库，模型上下文只包含这两类数据库证据。

### 比较知识切块参数

基线为 `KNOWLEDGE_CHUNK_SIZE=1000`、`KNOWLEDGE_CHUNK_OVERLAP=160`，切分器保留 Markdown 标题路径、FAQ 问题和 PDF 页码。可在隔离环境试验不同组合，使用同一份人工标注问题集比较召回与精确率。每次比较都要用新文档 revision 重新入库；只改参数不会改写已发布向量。不要只凭单题分数决定全量切块参数。

## 问题集字段

每个数组对象是一道固定问题：

- `query`：提交给搜梗 Agent 的中文问题。
- `expected_meme_names`：人工确认的相关梗标题；按标题计算 Recall@5、Recall@10 和 MRR。
- `expected_answer_fact_groups`：回答中应覆盖的事实词组。每个内层数组是一组可接受的同义表达，命中其中任意一个即算该组命中；组与组之间都需要覆盖。
- `expected_meme_ids`：可选的旧格式标签；仅在没有梗名标签时按 ID 计算相关性。
- `expected_empty`：可选布尔值，标记确认没有相关结果的问题，用于统计空结果准确率。

当前问题覆盖“背手负鼠”“哭哭马”“SBTI”“AI水果”“养龙虾”“不明觉厉”，以及问候、致谢和非梗问题负例。添加新问题时，请先检查梗库中确有对应条目，并由人工复核标题和事实标签，避免评估集把错误内容固定成标准答案。

## 校准向量相关度门槛

先将已发布梗回填到 pgvector，再运行：

```bash
python -m app.eval.retrieval_eval --calibrate-threshold --output reports/retrieval-threshold.json
```

校准模式直接读取未经过滤的向量分数，并按每题 `expected_meme_names` / `expected_empty` 标注评估。纯问候与致谢会和线上 Agent 一样跳过向量检索，不参与向量门槛判断。门槛选择按线上 `RETRIEVAL_TOP_K` 截断候选并选择 F1 最大值；并列时优先精确率，再优先较高门槛。校准报告保留门槛过滤后的前 10 条结果，分别计算 Recall@5 和 Recall@10。将报告中的 `threshold` 复核后写入 `VECTOR_MIN_SIMILARITY`，不要根据单个查询手工猜分数。空向量表或没有正例时校准会失败。

## 仅评估检索

从 `MemeAgent` 目录运行：

```bash
conda activate memeagent
python -m app.eval.retrieval_eval
```

此模式需要本地 `.env` 中可连接的 PostgreSQL/pgvector 和 embedding 配置，不请求聊天模型。可使用 `--dataset` 指定另一份问题集，也可用 `--output` 把完整 JSON 报告写入文件。

## 评估完整回答与 token 成本

```bash
python -m app.eval.retrieval_eval --include-answers
```

完整回答会调用实际 SSE 搜索入口，需要可用的数据库、embedding 与聊天模型配置。token usage 只在模型供应商支持时采集，默认关闭；需要时显式传入 `--stream-usage`。费用由调用者提供输入、输出 token 的每百万单价后估算，例如：

```bash
python -m app.eval.retrieval_eval --include-answers --stream-usage --input-price-per-million 0.15 --output-price-per-million 0.60 --output reports/memeagent-eval.json
```

未收到模型 token usage 或未提供两种单价时，费用显示为 `null`，不会把字符数或缺失数据当成 token 和零成本。请求 token usage 可能依赖服务商扩展；自定义 OpenAI 兼容端点不支持时，不要启用该开关。

## 报告指标

- `recall_at_5`、`recall_at_10`、`mrr`：带相关性标签的问题的平均检索排序指标。
- `empty_query_accuracy`：人工标记无结果的问题中，实际返回空候选的比例。
- `latency_p50_ms`、`latency_p95_ms`：检索耗时；完整回答模式下则是整次 SSE 搜索耗时。
- `answer_evidence_alignment_rate`：回答同时命中预期梗标题证据和全部事实词组的比例。
- `answer_fact_coverage`：预期事实词组的平均覆盖率。
- `time_to_first_token_p50_ms`、`time_to_first_token_p95_ms`：完整回答模式下首次收到非空回答片段的延迟。
- `estimated_cost_usd_total`、`estimated_cost_usd_average`：根据模型返回 usage 和所给单价估算的费用。
- 每道问题还会记录预期标签、检索到的 ID 和标题、回答、引用、错误、usage 以及单题指标。

回答依据指标使用标题和固定词组进行自动比对，是用于版本间回归的启发式信号，不等价于人工事实审查。重要提示词或模型变更后，应抽查答案与引用，必要时修订事实词组。

## 自动化回归

CI 运行离线 pytest 和黄金问题集结构校验，不连接数据库、不使用模型密钥，也不产生 API 费用。真实检索和完整回答评估仍在本地配置好数据库与服务凭据后手动运行。

## LangSmith（可选）

如需调试模型调用链，可配置 LangSmith tracing；先确认追踪服务的数据保留和访问范围适合查询内容，再在本地启用。
