# MemeAgent 检索与回答评估

评估集保存在 `golden_queries.json`，目前以固定梗名作为标签，避免不同数据库中的自增 ID 让结果失去可比性。标签和事实词组由项目维护者人工确认；每次修改检索、提示词或模型配置后，使用同一问题集比较指标。

## 问题集字段

每个数组对象是一道固定问题：

- `query`：提交给搜梗 Agent 的中文问题。
- `expected_meme_names`：人工确认的相关梗标题；按标题计算 Recall@5、Recall@10 和 MRR。
- `expected_answer_fact_groups`：回答中应覆盖的事实词组。每个内层数组是一组可接受的同义表达，命中其中任意一个即算该组命中；组与组之间都需要覆盖。
- `expected_meme_ids`：可选的旧格式标签；仅在没有梗名标签时按 ID 计算相关性。
- `expected_empty`：可选布尔值，标记确认没有相关结果的问题，用于统计空结果准确率。
- `hint_meme_ids`：可选的 Java 候选梗 ID，模拟业务预检索上下文。
- `snippets`：可选的 Java 梗摘要数组，模拟业务传入的候选内容；对象字段沿用搜梗接口的 `meme_id`、`title`、`introduction`、`tags`。

当前问题覆盖“背手负鼠”“哭哭马”“SBTI”“AI水果”和“养龙虾”。添加新问题时，请先检查梗库中确有对应条目，并由人工复核标题和事实标签，避免评估集把错误内容固定成标准答案。

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
