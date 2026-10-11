# AI 流式任务脱离浏览器并可恢复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 AI 搜索在浏览器断开、刷新、离页或关闭后继续生成，并允许登录用户恢复事件流、显式取消及在 10 分钟 TTL 后自动清理。

**Architecture:** Java 创建并托管 AI 任务，独立消费现有 MemeAgent SSE；Redis 保存任务元数据和有序事件供断点回放，MySQL 仍保存会话与完整问答。Vue 进入 AI 搜索时查询当前任务并恢复 SSE；断开订阅不会取消后台任务，停止按钮调用专用取消 API。

**Tech Stack:** Java 17、Spring MVC/WebFlux、Project Reactor、StringRedisTemplate、Redisson、MyBatis-Plus、Vue 3、Pinia、Fetch ReadableStream、Node.js `node:test`。

**Spec:** `docs/superpowers/specs/2026-10-07-ai-stream-resume-design.md`

## Global Constraints

- Java 是任务生命周期、用户身份和会话归属的权威层；Python MemeAgent 继续只负责执行 AI 搜索并输出现有 SSE 事件。
- 任务状态和可回放事件存入 Redis；会话及完整问答继续以 MySQL 为准，不增加任务数据库表。
- 每个用户同一时间最多有一个运行中的 AI 任务。任务仍运行时再次提交不同请求返回冲突；相同 `requestId` 的重试返回同一任务。
- 任务 TTL 从创建时开始计时，固定默认 10 分钟。Redis 状态与事件在该期限到达时过期；Java 任务计时器同时取消其上游订阅。
- 浏览器连接只负责读取事件。断开浏览器订阅不会取消 Java 后台订阅；停止按钮调用独立取消接口。
- Java 服务进程重启后的任务续跑不在本次范围内。进程退出会中断本机上游订阅；Redis 中的任务数据仍会按 TTL 自动过期。
- 保留当前 `session`、`meta`、`cite`、`token`、`done`、`error` 事件名和 data 结构，只增加标准 SSE `id`。
- 所有接口都要求现有登录态。后端从认证上下文获取 `userId`，不信任请求体中的用户 ID。读取、恢复、取消任务时校验任务归属。
- 不保证 Java 进程崩溃或部署重启后的任务恢复；不增加数据库表或新的产品依赖。
- 每个新增字段和配置项均添加清晰中文注释；不新增 SQL DDL。
- 在当前 checkout 原地实施，保留现有未提交更改；不重置或暂存无关文件。AI 相关源文件已有改动，实施提交不得夹带这些既有改动。

## Review Focus

- 同一个 `requestId` 并发重发：只能创建一个任务、保存一条用户消息并调用一次 Python。归属 Task 2/3 的并发幂等测试。
- 两个标签页以不同 `requestId` 同时提交：同一用户只能有一个 `RUNNING` 任务。归属 Task 2 的原子占用测试。
- SSE 读端断开后任务继续、按游标恢复且不重复 token。归属 Task 3 与 Task 5/6 的断连回放测试。
- `done`、手动取消和 TTL 同时竞争：只有首个原子终态转换生效，且未完成回答不落库。归属 Task 3 的状态竞争测试。
- 其他用户、过期 ID 或已删除会话尝试查询/取消任务：拒绝访问或返回过期结果，不能影响任务。归属 Task 3/4 的权限测试。

---

### Task 1: 把 AI 搜索准备过程与订阅接口分开

**Files:**
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/ai/application/AiSearchPreparation.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/ai/application/IAiSearchService.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/ai/application/impl/AiSearchServiceImpl.java`
- Test: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/modules/ai/application/impl/AiSearchServiceImplTest.java`

**Interfaces:**
- Produce `AiSearchPreparation(Long sessionId, String requestId, Flux<ServerSentEvent<String>> events)`；record 每个组件写中文注释。
- Replace request-coupled `streamSearch` with `prepareSearch(Long userId, AiSearchStreamRequestDTO request)`；同步解析/校验会话和保存用户问题，返回会话信息与惰性上游 Flux。
- Keep existing answer buffering, generic error sanitation, timeout, cache fingerprint and done-only persistence behavior inside `AiSearchServiceImpl`.

- [ ] **Step 1: Write failing service tests**

Add `prepareSearchReturnsSessionAndRequestIdsBeforeSubscription`, `cancellingPreparationFluxDoesNotPersistBufferedAnswer`, and adapt existing event/persistence tests to consume `AiSearchPreparation.events()`. Assert IDs are available before consuming the Flux, cancellation does not save a partial answer, and existing `done`/error persistence assertions still hold.

- [ ] **Step 2: Run the focused test to verify the contract is missing**

Run from `just_because_meme_understands_you-backend`: `mvn -Dtest=AiSearchServiceImplTest test`
Expected: FAIL because `AiSearchPreparation` and `prepareSearch` do not exist yet.

- [ ] **Step 3: Implement the preparation contract**

Move the current synchronous request/session/history setup into `prepareSearch`, return the resolved session ID and normalized request ID with the existing lazy stream, and preserve all established error and persistence behavior. Keep Chinese comments on new record components.

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `mvn -Dtest=AiSearchServiceImplTest test`
Expected: PASS, including complete-answer persistence only after `done`.

### Task 2: Add Redis task storage, status model, and TTL configuration

**Files:**
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/ai/domain/AiStreamTaskStatus.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/ai/domain/AiStreamTaskSnapshot.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/ai/domain/AiStreamTaskEvent.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/ai/infrastructure/task/AiStreamTaskRedisStore.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/ai/infrastructure/config/MemeAgentProperties.java`
- Modify: `just_because_meme_understands_you-backend/src/main/resources/application.yml`
- Modify: `just_because_meme_understands_you-backend/src/main/resources/application-dev.yml`
- Test: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/modules/ai/infrastructure/task/AiStreamTaskRedisStoreTest.java`

**Interfaces:**
- `AiStreamTaskStatus`: `RUNNING`, `COMPLETED`, `FAILED`, `CANCELLED`, `EXPIRED`.
- `AiStreamTaskSnapshot`: `String taskId`, `Long userId`, `Long sessionId`, `String requestId`, `AiStreamTaskStatus status`, `long createdAtMillis`, `long expiresAtMillis`, `long lastEventId`; comment each component in Chinese.
- `AiStreamTaskEvent`: `long id`, `String event`, `String data`; comment each component in Chinese.
- `AiStreamTaskRedisStore`: `reserve(userId, requestId, taskId, nowMillis, expiresAtMillis)`, `findCurrent(userId)`, `find(taskId)`, `setSessionId(taskId, sessionId)`, `appendEvent(taskId, event, data)`, `readEvents(taskId, afterId, limit)`, `transition(taskId, terminalStatus)`, and `deleteTask(taskId)`.
- Use atomic Redis scripts for user/request reservation, event ID plus append, and first-terminal-state-wins. Store each event as JSON in a Redis list keyed by task ID; use its sequence number as the SSE ID. Apply the remaining task TTL to metadata, event list, user pointer, and idempotency key.
- Add `MemeAgentProperties.Ai.taskTtlSeconds` defaulting to `600`, with a Chinese field comment; configure `meme.ai.task-ttl-seconds: ${MEME_AI_TASK_TTL_SECONDS:600}` in shared YAML and the 600-second dev default in `application-dev.yml`, each with a Chinese config comment.

- [ ] **Step 1: Write failing Redis store tests**

Add tests `taskTtlDefaultsTo600Seconds`, `reserveIsIdempotentForSameUserAndRequestId`, `reserveRejectsDifferentRequestWhileUserTaskRuns`, `readsOnlyEventsAfterCursor`, `expiresAllTaskKeysAtConfiguredDeadline`, and `terminalTransitionCannotOverwriteExistingTerminalState`. Mock `StringRedisTemplate` responses and assert atomic scripts receive the user/request/task keys and 600-second expiry.

- [ ] **Step 2: Run the focused test to verify the store contract is missing**

Run: `mvn -Dtest=AiStreamTaskRedisStoreTest test`
Expected: FAIL because task types and Redis store do not exist.

- [ ] **Step 3: Implement the task model, Redis store, and TTL setting**

Use `StringRedisTemplate` for hashes, lists, counters, and TTLs; use `DefaultRedisScript` for multi-key atomic operations. Store no query text in task metadata. Include Chinese comments on every added field/config item.

- [ ] **Step 4: Run store tests and configuration binding checks**

Run: `mvn -Dtest=AiStreamTaskRedisStoreTest test`
Expected: PASS; reservation, replay cursor, state transition, and TTL assertions pass.

### Task 3: Implement detached task orchestration, replay, cancel, and expiry

**Files:**
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/ai/application/IAiStreamTaskService.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/ai/application/impl/AiStreamTaskServiceImpl.java`
- Test: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/modules/ai/application/impl/AiStreamTaskServiceImplTest.java`

**Interfaces:**
- `submit(Long userId, AiSearchStreamRequestDTO request) -> AiStreamTaskSnapshot`
- `getCurrent(Long userId) -> Optional<AiStreamTaskSnapshot>`
- `events(Long userId, String taskId, long afterEventId) -> Flux<ServerSentEvent<String>>`
- `cancel(Long userId, String taskId) -> AiStreamTaskSnapshot`
- `cancelBySession(Long userId, Long sessionId) -> void`
- Subscribe to `IAiSearchService.prepareSearch` independently of any HTTP response; hold each worker in a local `Disposable.Swap`; append events after `publishOn(Schedulers.boundedElastic())` so Redis I/O does not block the WebClient event loop. Use Redisson Pub/Sub to deliver cross-instance cancellation to the worker owner.
- Poll Redis event lists from `events` with a 250 ms interval, emitting only IDs greater than `afterEventId`; finish when the task is terminal or absent. Attach `id`, `event`, and original `data` to each SSE response.
- Schedule blocking Redis event-list reads on `Schedulers.boundedElastic()`; do not run `StringRedisTemplate` calls on Reactor Netty or servlet request threads.
- Buffer the `done` event until the preparation Flux completes, because `AiSearchServiceImpl` persists the complete answer after emitting `done`; append `done` and mark `COMPLETED` only after that persistence stage completes. On stream error, generic error event, manual cancellation, or TTL, atomically set the terminal status and dispose the upstream subscription.

- [ ] **Step 1: Write failing orchestration tests**

Add `detachedWorkerContinuesAfterEventsSubscriberCancels`, `reconnectReplaysOnlyEventsAfterLastId`, `cancelDisposesPythonSubscription`, `ttlDisposesWorkerAndMarksExpired`, `doneIsPublishedOnlyAfterAnswerPersistenceCompletes`, and `firstTerminalTransitionWinsWhenCancelRacesWithDone`. Mock `AiStreamTaskRedisStore` and use a controllable `Flux.create` source; inject a Reactor `Scheduler` so TTL tests use virtual time.

- [ ] **Step 2: Run the focused test to verify orchestration is missing**

Run: `mvn -Dtest=AiStreamTaskServiceImplTest test`
Expected: FAIL because the task service does not exist.

- [ ] **Step 3: Implement the coordinator and its cancellation lifecycle**

Reserve idempotently before preparing a search, store the resolved session ID, then subscribe a worker without tying it to the requesting HTTP exchange. Keep one `RUNNING` task per user. Make repeated cancellation idempotent; delete the local worker and timer after any terminal transition. Keep task and event keys on the original 10-minute creation deadline.

- [ ] **Step 4: Run orchestration and existing AI service tests**

Run: `mvn -Dtest=AiStreamTaskServiceImplTest,AiSearchServiceImplTest test`
Expected: PASS, including detached execution, expiry, cancellation, and done-only persistence.

### Task 4: Expose authenticated task APIs and cancel on session deletion

**Files:**
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/vo/AiStreamTaskVO.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/controller/AiSearchController.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/ai/application/impl/AiChatSessionServiceImpl.java`
- Test: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/api/AiSearchControllerTest.java`
- Test: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/modules/ai/application/impl/AiChatSessionServiceImplTest.java`
- Modify: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/api/RestfulApiRouteContractTest.java`

**Interfaces:**
- Map snapshots to `AiStreamTaskVO` with `taskId`, `sessionId`, `requestId`, `status`, and `expiresAtMillis`; document each field in Chinese.
- Change `POST /v1/ai/searches` to return `Result<AiStreamTaskVO>` after `submit`.
- Add `GET /v1/ai/searches/current`, `GET /v1/ai/searches/{taskId}/events?after={eventId}` with `text/event-stream`, and `POST /v1/ai/searches/{taskId}/cancel`.
- Every method derives user ID via `AuthContext.requireCurrentUserId`; task ownership is checked in the task service before exposing metadata or events.
- After a successful session soft delete, call `cancelBySession` for any still-running task tied to that session.

- [ ] **Step 1: Write failing controller and delete-session tests**

Create `AiChatSessionServiceImplTest` and add route assertions for all four endpoints, a controller test verifying the authenticated user ID is passed to each service method, cross-user task lookups returning not found/forbidden, and a session-service test verifying a matching running task is cancelled after a successful soft delete.

- [ ] **Step 2: Run the focused tests to verify the API is missing**

Run: `mvn -Dtest=AiSearchControllerTest,RestfulApiRouteContractTest,AiChatSessionServiceImplTest test`
Expected: FAIL because the routes, task VO, and cancellation hook are absent.

- [ ] **Step 3: Implement controller responses and deletion integration**

Keep SSE event names and payloads unchanged; return task metadata as JSON at creation and use the dedicated events route for replay. Add Chinese comments to new VO fields.

- [ ] **Step 4: Run focused API and session tests**

Run: `mvn -Dtest=AiSearchControllerTest,RestfulApiRouteContractTest,AiChatSessionServiceImplTest test`
Expected: PASS; all routes are versioned and ownership is enforced.

### Task 5: Add frontend task APIs and resumable SSE event parsing

**Files:**
- Create: `Just_because_meme_understands_you-frontend/src/utils/aiStreamSse.mjs`
- Create: `Just_because_meme_understands_you-frontend/src/utils/aiStreamLifecycle.mjs`
- Modify: `Just_because_meme_understands_you-frontend/src/api/ai.js`
- Test: `Just_because_meme_understands_you-frontend/tests/aiStreamSse.test.mjs`
- Test: `Just_because_meme_understands_you-frontend/tests/aiStreamLifecycle.test.mjs`

**Interfaces:**
- Export `readAiStreamSse(response, handlers)` from `aiStreamSse.mjs`; parse `id:`, `event:`, and multi-line `data:` across chunk boundaries and dispatch the six existing event names plus the event ID. Update the event cursor only after the handler succeeds.
- Export pure `nextAiStreamRetryDelay(attempt)` and `shouldReconnectAiTask({status, error, stopped, expiresAt, now})` decisions from `aiStreamLifecycle.mjs` for store use and Node tests.
- Add `createAiSearchTask(payload)`, `getCurrentAiSearchTask()`, `streamAiSearchTask(taskId, afterEventId, handlers)`, and `cancelAiSearchTask(taskId)` in `api/ai.js`.
- Use existing `request` for JSON operations and `streamRequest` with `GET` for event subscriptions so existing credentials and token-refresh behavior remain in effect.

- [ ] **Step 1: Write failing Node tests for the SSE parser**

Add tests for split UTF-8/event chunks, CRLF separators, JSON event data, event IDs, multiple events in one chunk, and EOF with a final complete event. Add lifecycle tests proving retry delays are capped at 4 seconds and reconnection stops for abort, auth failure, terminal state, explicit stop, or expired task.

- [ ] **Step 2: Run the focused Node test to verify the parser is missing**

Run from `Just_because_meme_understands_you-frontend`: `node --test tests/aiStreamSse.test.mjs tests/aiStreamLifecycle.test.mjs`
Expected: FAIL because the parser module does not exist.

- [ ] **Step 3: Implement the parser and task API wrappers**

Keep parsing transport concerns out of the Pinia store. Report the latest event ID after its event callback succeeds so a failed callback does not skip an event during resume.

- [ ] **Step 4: Run the focused parser test and frontend lint**

Run: `node --test tests/aiStreamSse.test.mjs tests/aiStreamLifecycle.test.mjs` and `npm run lint -- --no-fix --ext .js,.vue,.mjs`
Expected: parser tests pass and ESLint reports no new errors.

### Task 6: Restore task state in Pinia and make stop explicit

**Files:**
- Modify: `Just_because_meme_understands_you-frontend/src/stores/aiChat.js`
- Modify: `Just_because_meme_understands_you-frontend/src/views/AiSearch.vue`
- Modify: `Just_because_meme_understands_you-frontend/src/api/ai.js`

**Interfaces:**
- Add store actions `resumeCurrentTask()`, `detachTaskEvents()`, and `stopCurrentTask()`; keep `abortStream()` only as an alias for detaching if other call sites still depend on it.
- Track `taskId`, `requestId`, `taskStatus`, `lastEventId`, event-reader `AbortController`, and ephemeral assistant message ID in Pinia; add a clear Chinese comment for every new field.
- `sendQuery` creates a task first, then reads its SSE events. `beforeUnmount`, `startNewChat`, and `selectSession` detach the event reader only. Composer Stop calls `stopCurrentTask`, which awaits the cancel API and then detaches.
- On mount, query current task; load its session messages; if running, create/rebuild the temporary assistant bubble and subscribe after the saved event ID. On refresh with no in-memory cursor, replay from ID 0. If completed, reload persisted messages; if failed/cancelled, replay the terminal event and keep partial text out of persistence.
- When switching chats during generation, retain task identity and cursor separately from the visible session; never append task tokens to a different session. Resume when returning to the task session, and reject a new submission until the task ends or is explicitly stopped.
- Reconnect fetch after transport errors using capped exponential delays (250 ms, 500 ms, 1 s, 2 s, 4 s) while the task is `RUNNING` and unexpired. Do not reconnect on `AbortError`, terminal status, authentication failure, or explicit stop.
- A different request submitted while the user task is running shows an actionable wait/stop message instead of silently cancelling the existing task.

- [x] **Step 1: Implement Pinia recovery and explicit cancellation**

Make route destruction abort only the browser reader. Reconstruct a fresh page from server task metadata and replayed events; never append tokens twice when `eventId <= lastEventId`.

- [x] **Step 2: Lint and build the frontend**

Run: `npm run lint -- --no-fix` and `npm run build`
Expected: lint and production build succeed.

- [ ] **Step 3: Inspect the AI search behavior**

Manually verify submit, navigate away/back, stop, refresh during generation, and restore a completed task using the running local Java/Redis/MemeAgent services. Pending: the local AI page redirects to `/login`; no authenticated browser session is available, so these UI flows have not been manually exercised.

### Task 7: Run integrated lifecycle verification and document the result

**Files:**
- Modify: `MemeAgent/app/agents/prompt_files/search_system_zh.md`
- Modify: `MemeAgent/app/agents/prompts.py`
- Modify: `MemeAgent/app/agents/search_agent.py`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/ai/application/impl/AiSearchServiceImpl.java`
- Test: `MemeAgent/tests/test_security_sanitize.py`
- Test: `MemeAgent/tests/test_meme_agent.py`
- Test: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/modules/ai/application/impl/AiSearchServiceImplTest.java`
- Modify: `docs/superpowers/specs/2026-10-07-ai-stream-resume-design.md`

**User steering:** The user added that user-facing output must contain only results, without retrieval/process narration, and supplied an example of an answer that exposed retrieval status. When no usable evidence is available, answer exactly `不知道。` and do not add unsupported filler. Update the managed system prompt, increment the Python prompt and Java cache pipeline versions together, and retain execution details only as structured server diagnostics. Existing logs may retain stage, status, elapsed time, hit count, retry count, fallback type, and error type; do not log raw user questions, retrieved text, or model reasoning.

- [x] **Step 1: Run focused backend regression tests**

Run from backend: `mvn -f pom-task-test.xml '-Dtest=AiSearchServiceImplTest,AiStreamTaskRedisStoreTest,AiStreamTaskServiceImplTest,AiSearchControllerTest,AiChatSessionServiceImplTest,RestfulApiRouteContractTest' test`
Expected: PASS with Redis script, task lifecycle, authorization, session, and existing stream tests green.

- [x] **Step 2: Verify result-only answer policy and prompt versioning**

Run from `MemeAgent`: `conda run -n memeagent python -m pytest tests/test_security_sanitize.py tests/test_meme_agent.py -q`. Also run the Java AI service test and confirm its cache pipeline version matches `meme-search-v4`.
Expected: the managed prompt and deterministic fallback forbid retrieval/process narration, prompt injection boundaries remain, and Python/Java cache versions match.

- [x] **Step 3: Run focused frontend checks**

Run from frontend: `node --test tests/aiChatStore.test.cjs tests/aiStreamSse.test.mjs tests/aiStreamLifecycle.test.mjs`; then `npm run lint -- --no-fix --ext .js,.vue,.mjs` and `npm run build`.
Expected: tests, lint, and production build pass.

- [ ] **Step 4: Verify authenticated browser lifecycle scenarios against local services**

Submit one query and verify: switching away leaves Python generation active; reconnecting with the last event ID resumes without duplicated tokens; F5 discovers the task; closing/reopening after completion shows the saved answer; Stop disposes the upstream stream; a test TTL override expires and removes task keys; a second tab cannot start a different task for the same user. Pending for the same authentication limitation recorded in Task 6; automated API, Redis lifecycle, and frontend parser/store tests passed.

- [x] **Step 5: Review final diff for unrelated changes**

Run `git diff --check` and inspect only files changed for this feature; confirm prior uncommitted work remains present and no unrelated file is staged or committed.
