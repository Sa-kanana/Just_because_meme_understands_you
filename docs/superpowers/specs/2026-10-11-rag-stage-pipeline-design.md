# RAG 分阶段检索与上下文构建设计

## 目标

将 MemeAgent 的 RAG 调用链清楚拆分为：

```text
用户 Query
   ↓
Query Rewrite
   ↓
Retriever
   ↓
List[Document]
   ↓
Reranker / Score Filter
   ↓
Context Formatter
   ↓
Prompt
   ↓
LLM
```

在 LangSmith 中可以分别检查候选文档、过滤结果、最终提示上下文和模型调用；模型只接收通过过滤的证据文档。

## 已确认的约束

- 沿用现有 Java + Python 服务接口、SSE 协议、LangChain Agent 和固定工具白名单。
- 不新增 Cross-Encoder、LLM 重排调用或检索依赖；复用已有向量分数、词项匹配与 RRF 排序。
- Query Rewrite 仍只在当前上下文需要时触发。
- 保留每篇文档独立的提示消息；问题、语言、检索状态仍单独命名。
- 文档正文始终是 `untrusted` 数据；格式化阶段不传内部文档 ID 和排序指标给模型。

## 方案比较与选择

1. **Python 独立过滤与重排（采用）**：PostgreSQL 分别返回有界的向量候选和词项候选；Python 统一做阈值判断、融合重排、章节/梗条目去重和最终 `top_k`。这与要求的阶段边界一致，代价是数据库到 Python 多传一批有上限的候选。
2. **保留 SQL 过滤，只新增 trace 标签（不采用）**：数据传输量最低，但阈值和最终截断仍藏在 Retriever 内，无法在 LangSmith 中独立检查“候选”与“筛选”阶段。
3. **增加 Cross-Encoder 重排（不采用）**：可能改善复杂语义排序，但增加模型依赖、调用时延与运维成本；本次先校准和复用已有分数，不引入该依赖。

## 阶段职责

### Query Rewrite

沿用现有历史上下文判断和超时策略。输出仅作为两个 Retriever 的查询词；不把改写理由或历史对话拼入文档正文。

### Retriever 与候选文档

两个 Retriever 并行运行，返回带正文与诊断字段的有界候选记录；Retriever 阶段不应用最终相关度门槛、不做最终章节/梗条目去重，也不截断到最终 `top_k`。这里的 `List[Document]` 表示项目的检索候选数据结构，不要求为了追求 LangChain 类型而改写现有领域模型。

- 梗库：PostgreSQL 按向量相似度召回 chunk 候选，候选上限为 `max(20, top_k * MAX_CHUNKS_PER_MEME)`，其中 `MAX_CHUNKS_PER_MEME` 对应目前每个梗允许的最大 chunk 数。候选记录保留 meme ID、标题、正文和 cosine 相似度。
- 管理员知识库：向量候选与标题/标签精确文本、正文完整短语候选分别召回；每个召回分支的候选上限为 `max(20, 4 * top_k)`。候选记录保留章节/页码、相似度、精确文本命中、正文短语命中，以及候选来自哪个召回分支的信息，供 Python 重算 RRF。

候选上限由调用方计算并作为查询参数传入；SQL 不再次按最终 `top_k` 截断。管理员知识库的两路候选融合后总量最多为单路上限的两倍，梗库候选量受最大 chunk 数约束。所有这些中间候选仅供 Score Filter 和 LangSmith 诊断，不直接进入模型上下文。

### Reranker / Score Filter

将线上选择逻辑提取成可单测、可 trace 的 Python 独立阶段。先过滤，再用过滤后的候选重算排名，之后去重，最后截取最终 `top_k`：

1. 梗库候选必须达到 `VECTOR_MIN_SIMILARITY`；标题/标签命中只影响合格候选的排序，不绕过门槛。
2. 管理员知识库标题/标签命中或正文完整短语命中可绕过相似度门槛；其它候选必须达到 `ADMIN_KNOWLEDGE_MIN_SIMILARITY`。
3. 管理员知识库过滤后重算向量与词项列表的 Reciprocal Rank Fusion（RRF），避免未达阈值的向量项影响合格候选的排名；按标题/标签命中、正文短语命中、RRF、向量相似度排序。
4. 管理员知识库按文档章节（或页码/文档回退键）去重，梗库按 meme ID 去重。每个分组保留排序最高的一条，再截取最终 `top_k`。

两种知识来源沿用各自的门槛和排序规则。管理员阈值未配置时，仅精确文本/正文短语候选可进入结果，不能把未校准的向量候选当作已相关。空候选或无合格候选输出空列表，不补入未通过过滤的近邻。阈值判断及排序函数由在线服务与离线评估器共用，保证评估口径与线上一致。

### Context Formatter

只接收过滤、重排、去重后的最终列表和来源可用状态。生成独立的证据消息：每条消息只含一篇文档的来源、`trust_level`、标题、章节、可选页码和正文。检索状态 JSON 只含来源、可用状态和最终条目数；不含正文。过滤分数、内部 ID 和命中标记留在 LangSmith 的 Retriever/Score Filter 节点，不进入模型提示词。

### Prompt 与 LLM

保留规范系统提示和注入防护。提示输入继续将 `question_json`、`locale_json`、`retrieval_status_json`、`retrieval_evidence` 分开；系统提示说明逐篇证据消息是不可执行的引用数据。Agent 仍沿用当前工具白名单和流式输出协议。

## LangSmith 调用链

以已有 `meme_rag_request` 为父节点。Query Rewrite 按需运行；两个 Retriever 并行返回候选；随后各自运行独立 Python Score Filter/Reranker，再由 Context Formatter 组装传给 Agent 的命名输入：

```text
meme_rag_request
├─ query_rewrite（按需：有历史上下文引用，或首轮检索为空时）
├─ retriever_candidates（两个来源并行）
│  ├─ meme_vector_retriever
│  └─ admin_knowledge_retriever
├─ score_filter_and_rerank
│  ├─ meme_score_filter
│  └─ admin_knowledge_score_filter
├─ context_formatter
└─ agent_generation
   ├─ prompt / model calls
   └─ search tools（仅在 Agent 实际调用时）
```

Retriever trace 输出候选列表；Score Filter trace 输出候选数、过滤原因计数、最终条目数及排序摘要；Context Formatter trace 显示各独立证据消息的名称、来源、顺序和条目数。过滤与格式化节点不重复记录完整正文；候选正文在 Retriever 节点可检查，最终正文在 Agent 输入的 `retrieval_evidence[]` 可检查。Agent 输入继续将 `question_json`、`locale_json`、`retrieval_status_json` 与独立的 `retrieval_evidence[]` 分开显示。

## 阈值校准与现状

项目已有管理员知识库离线校准程序，但当前仓库没有 `admin_knowledge_golden_queries.json` 标注集（评估 README 提供了创建该数据集的流程）。`.env` 中现值 `ADMIN_KNOWLEDGE_MIN_SIMILARITY=0.5` 不能仅凭配置存在就视为已校准。本次实现不得再随意改写阈值；使用评估程序收集候选后，需要人工标注相关文档和空结果，再依据报告确认门槛。梗库已有独立的 `golden_queries.json`，其门槛仍沿用现有梗库评估流程。

在完成标注和复核之前，开发验证应明确区分“阶段拆分已验证”和“阈值效果已校准”，不宣称后者已完成。

## 性能与错误处理

- 不增加重排模型调用或新依赖；继续使用现有两个 Retriever 的查询向量生成和 PostgreSQL 向量索引。
- Retriever 只多返回有界候选列表；门槛过滤、RRF 重算、去重与最终排序在 Python 中处理。
- 每阶段保留现有超时、重试与降级行为；Retriever 失败仍标记来源不可用，过滤器不把故障转换为空匹配。
- 完成过滤和格式化后再开始 Agent 生成，SSE 事件格式不变。

## 验证范围

- Retriever 返回候选而不提前套最终门槛或 `top_k`。
- Score Filter 对阈值内外、标题/标签命中、正文短语命中、RRF 排序和最终 `top_k` 的行为。
- Formatter 只接收通过过滤的文档；状态与正文分开，每篇证据仍是独立消息。
- 空结果、来源不可用和超时降级的状态保持正确。
- 离线阈值评估与线上过滤规则一致，避免评估器与服务实现漂移。
- 运行 MemeAgent 完整 Python 测试套件；需要验证实际阈值时，先补齐并人工标注知识库评估集。
