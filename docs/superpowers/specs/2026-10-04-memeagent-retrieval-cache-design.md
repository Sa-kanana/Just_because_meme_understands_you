# MemeAgent 请求内检索复用设计

状态：待用户审阅

日期：2026-10-04

## 背景

`MemeAgent` 的 `/stream` 处理先调用 `retrieve_meme_chunks`，立即发送 `meta` 和 `cite` SSE 事件；随后创建的 LangChain Agent 又被要求调用 `search_meme_knowledge`。该工具会再次调用 `retrieve_meme_chunks`。当两次检索参数相同时，会重复生成查询 embedding 并访问 pgvector。

目前这条路径没有专门测试检索调用次数。现有 MemeAgent 单元测试基线为 6 项通过；现有评估集只有一个查询且没有预期梗 ID，不能用来声称线上延迟或检索质量改善。

## 目标

- 在同一 `/stream` 请求内，复用前置检索成功结果，避免 Agent 工具对完全相同检索条件重复生成 embedding 和查询 pgvector。
- 保留现有 `meta`、`cite`、`token`、`done` / `error` SSE 契约、顺序和 fallback 语义。
- 当 Agent 改写查询或改变候选 ID / `top_k` 时，继续执行正常检索，避免复用语义不同的结果。
- 使用不依赖外部模型、Firecrawl 或 pgvector 的测试验证检索调用次数。

## 非目标

- 不改变提示词、Agent 工具选择策略、检索排序、引用内容或回答质量策略。
- 不取消 Agent 的 `search_meme_knowledge` 工具调用；本方案不减少用于决定工具调用的 LLM 编排开销。
- 不建立跨请求或进程级缓存，不引入 Redis、数据库表或新依赖。
- 不修改 Java SSE 桥接、前端事件解析和 HTTP API。
- 不改进热梗采集、知识灌库、鉴权或其他独立子系统。

## 方案与取舍

采用请求级缓存，复用 `/stream` 已完成的确定性检索结果。相较于取消前置检索，这种方式保留快速发送引用和无模型 fallback；相较于移除检索工具，它也保留 Agent 在查询被改写时再次检索的能力。缓存命中只节省重复 embedding 与向量数据库访问，不消除 Agent 自身的工具调用。

## 结构与数据流

1. `stream_ai_search` 使用现有 `safe_query`、候选 meme ID、snippets 和配置中的 `retrieval_top_k` 执行前置检索。
2. 前置检索成功后，继续按现有顺序发送 `meta` 和去重后的 `cite`，并将该结果放入当前请求的检索缓存。
3. 创建 `search_meme_knowledge` 工具时，将同一个请求级缓存传入工具闭包。
4. 工具按有效查询文本、有效候选 meme ID 列表和 `top_k` 查找缓存。键完全相同时，将缓存结果序列化为原有工具 payload；未命中时，调用原有 `retrieve_meme_chunks` 实现。
5. 缓存只在当前 `stream_ai_search` 调用中存在。前置检索成功结果是预填充项；缓存 miss 沿用既有检索流程，不改变错误处理或 SSE 事件。

查询键使用相同的已清理查询文本，不做语义归一化。候选 ID 顺序和 `top_k` 都参与匹配。snippets 不由工具参数修改，并由请求级闭包固定，因此无需跨请求签名。有效参数不完全相同则安全地 miss。

## 错误处理与兼容性

- 前置检索失败时，不写入缓存；现有 stream 错误事件路径保持不变。
- 缓存命中不执行新的 IO，也不吞掉异常。
- 缓存 miss 执行现有检索函数；工具异常仍走当前 Agent fallback / error 逻辑。
- 不把异常、空的临时状态或部分向量结果写入缓存。
- 不改变 SSE 数据结构、事件名称、发送顺序、HTTP 接口或前端行为。
- 对不同查询键的重复调用不承诺单飞去重；本设计只处理已确认的“前置检索后 Agent 再检索相同请求”路径。

## 测试与验收

新增异步测试，不访问真实 OpenAI、Firecrawl、MySQL 或 pgvector：

- 使用相同查询、候选 ID 和 `top_k` 调用检索工具时，返回预填充结果且底层检索调用总数保持为 1。
- 任一检索键字段变化时，调用原检索实现，而不复用前置结果。
- 两个独立请求的缓存互相隔离。
- 使用假的 Agent 验证 SSE 仍先产生 `meta` / `cite`，随后产生 token 和终止事件；fallback 路径继续使用前置检索结果。

验收以重复检索调用次数从 2 降至 1 为准，不将 mock 测试折算为真实毫秒延迟。现有 Python 测试基线为 `conda run -n memeagent pytest -q tests/test_meme_agent.py tests/test_retrieval_merge.py tests/test_schemas.py`，结果为 6 passed。

## 风险与限制

- 如果模型工具调用改写了查询，缓存将 miss；这会保留额外检索，但确保语义不同的查询不会复用旧结果。
- 如果请求 `top_k` 与 Agent 工具实际使用的 `top_k` 不同，缓存将 miss；正确性优先于强制复用。
- 单元测试证明调用次数和事件契约，不提供模型、网络及真实 pgvector 环境下的延迟数据。
