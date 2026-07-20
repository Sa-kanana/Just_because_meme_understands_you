# LangSmith 评估

黄金集示例：`eval/golden_queries.json`（可按需新增）。

```bash
conda activate memeagent
export LANGCHAIN_TRACING_V2=true
export LANGCHAIN_API_KEY=...
export LANGCHAIN_PROJECT=meme-agent-eval
pytest tests/ -q
```

评估维度建议：

- 检索 Recall@K（期望 meme_id 是否命中）
- 回答是否包含有效 meme_id 引用
- 无检索结果时是否诚实拒答
