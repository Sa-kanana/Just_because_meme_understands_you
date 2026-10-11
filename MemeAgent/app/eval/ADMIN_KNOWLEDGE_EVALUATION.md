# 管理员知识检索门槛校准

管理员知识检索使用独立的 `ADMIN_KNOWLEDGE_MIN_SIMILARITY`。在提供人工标注数据之前不要填写该配置；未校准时系统仅返回查询中精确包含的文档标题或标签命中，不把宽泛的向量近邻当成证据。

评估集为 JSON 数组，每条记录至少包含 `query`，并标注一个或多个 `expected_doc_ids`，或用 `expected_empty: true` 标明应为空。文档 ID 必须对应 MySQL 管理员知识文档 ID。

```json
[
  {"query": "不明觉厉是什么意思", "expected_doc_ids": ["123456789"]},
  {"query": "完全无关的问题", "expected_empty": true}
]
```

在 MemeAgent 环境运行：

```powershell
python -m app.eval.admin_knowledge_eval path/to/admin_knowledge_golden_queries.json --output reports/admin-knowledge-threshold.json
```

确认报告的精度、召回和空结果准确率符合预期后，将报告中的 `threshold` 配置为 `ADMIN_KNOWLEDGE_MIN_SIMILARITY`，再用新增的正例、难负例与空结果持续评估。
