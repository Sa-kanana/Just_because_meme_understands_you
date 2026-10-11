# 已发布梗编辑审核实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让作者提交已发布梗的修改稿并经管理员审核后替换正式版本，同时保留审核期间的旧版本和所有互动数据。

**Architecture:** 在 meme 模块增加独立修改稿快照与审核服务；用户提交不会改写正式梗，管理员通过后在事务中替换可编辑字段、标签和资源。用户端复用发布表单，管理端在梗管理页增加修改审核队列。

**Tech Stack:** Java 17、Spring Boot、MyBatis-Plus、MySQL 8、Vue 3、Vue Router、Vuesax、Arco Design。

**Spec:** `docs/superpowers/specs/2026-10-10-published-meme-edit-design.md`

## Global Constraints

- 不新增微服务或外部依赖。
- 梗状态 `1` 和 `3` 可提交修改；`2`、`5`、`6`、`4` 遵守设计文档中的限制。
- 待审修改不得更改正式梗的可见性；审核不得更改 ID、作者、发布时间、互动计数或关系数据。
- 每个 SQL DDL 列都写中文 `COMMENT`；脚本字段复制逐项注释来源和目标。
- 所有用户和管理员身份均从认证上下文读取，不信任请求体中的身份、状态和计数。
- 只修改本功能所需的文件；保留工作区已有的未提交修改。

## Review Focus

- 非作者尝试编辑或读取待审稿时必须被拒绝；由 Task 2 的服务测试覆盖。
- 同一梗并发提交重复待审稿时只允许一条；由 Task 2 的提交服务测试覆盖。
- 管理员下架、锁定或永久删除与待审修改交错时，审核不得恢复公域状态；由 Task 3 的审核服务测试覆盖。
- 修改稿驳回和资源替换不能删除正式版本仍在使用的 OSS 文件；由 Task 3 的资源审核测试覆盖。
- 下架梗必须能预览隐藏资源；审核替换资源时归档旧记录，避免重新上架时旧资源复活。
- 生命周期状态更新与内容审核使用同一梗行锁；OSS 清理需要保护待审修改稿引用。
- 作者修改封面、标签或资源失败时表单仍保留原输入，详情页/个人列表不误显示未批准内容；由 Task 4 的前端流程验证覆盖。

---

### Task 1: 修改稿数据模型与迁移

**Files:**
- Existing: `just_because_meme_understands_you-backend/src/main/resources/db/migration/V2026101002__meme_edit_revisions.sql` (already tracked in the project baseline)
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/domain/entity/MemeEditRevision.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/infrastructure/persistence/mapper/MemeEditRevisionMapper.java`
- Test: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/db/MemeEditRevisionSchemaContractTest.java`

**Interfaces:**
- Produces: `MemeEditRevisionMapper extends BaseMapper<MemeEditRevision>` and persisted fields defined in the spec.
- `content_json` stores the complete editable payload; statuses are `PENDING`, `APPROVED`, `REJECTED`, `CANCELED`.

- [x] **Step 1: Write schema contract tests** asserting all required columns and Chinese comments exist, status defaults to `PENDING`, and indexes cover `(meme_id, status)` and `(status, created_at, id)`.
- [x] **Step 2: Run `mvn -f just_because_meme_understands_you-backend/pom.xml -Dtest=MemeEditRevisionSchemaContractTest test` and confirm it fails** because the migration resource does not exist.
- [x] **Step 3: Add the migration, entity, and mapper**. Include a Chinese `COMMENT` for every SQL column and Chinese field comments on the entity. Keep completed revision rows for audit; do not add a partial unique index.
- [x] **Step 4: Run `mvn -f just_because_meme_understands_you-backend/pom.xml -Dtest=MemeEditRevisionSchemaContractTest test` and confirm it passes.**

### Task 2: 用户提交修改稿与个人主页状态

**Files:**
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/dto/MemeEditRequestDTO.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/vo/MemeEditRevisionVO.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/application/MemeEditRevisionService.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/controller/MemeController.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/vo/UserPublishedMemeVO.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/contract/MemeCatalogPort.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/application/MemeCatalogApplicationAdapter.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/infrastructure/persistence/mapper/MemeEditRevisionMapper.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/user/application/profile/impl/UserProfileServiceImpl.java`
- Test: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/modules/meme/application/MemeEditRevisionSubmitTest.java`

**Interfaces:**
- `MemeEditRevisionService.submitEdit(Long userId, Long memeId, MemeEditRequestDTO request): MemeEditRevisionVO`
- `MemeCatalogPort.findLatestEditRevisionsByMemeIds(Collection<Long> memeIds): Map<Long, MemeEditRevisionSummary>`
- `MemeCatalogPort.MemeEditRevisionSummary(String status, LocalDateTime createdAt, String reviewNote)`
- `MemeController.updateOwnMeme(String memeId, MemeEditRequestDTO body, HttpServletRequest servletRequest): Result<MemeEditRevisionVO>` at `PUT /v1/memes/{memeId}`.
- `UserPublishedMemeVO` adds the latest edit revision status, submission time, and review note when the requester is the owner.

- [x] **Step 1: Add failing service tests** for author submission on status `1`/`3`, non-owner rejection, other status rejection, invalid tags/resources, and rejection of an existing `PENDING` revision.
- [x] **Step 2: Run `mvn -f just_because_meme_understands_you-backend/pom.xml -Dtest=MemeEditRevisionSubmitTest test` and confirm the expected failures.**
- [x] **Step 3: Implement owner/status validation, normalized JSON snapshot and deterministic base-content SHA-256.** Lock the parent meme row in the transaction before checking for a pending revision and inserting one.
- [x] **Step 4: Expose `PUT /v1/memes/{memeId}` and owner-only latest revision status in the user's published meme page response.** The request DTO contains only editable content fields.
- [x] **Step 5: Run `mvn -f just_because_meme_understands_you-backend/pom.xml -Dtest=MemeEditRevisionSubmitTest test` and confirm all cases pass.**

### Task 3: 管理端审核与正式内容应用

**Files:**
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/admin/controller/AdminMemeEditRevisionController.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/admin/dto/AdminMemeEditReviewRequestDTO.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/admin/vo/AdminMemeEditRevisionVO.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/application/MemeEditRevisionReviewService.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/infrastructure/persistence/mapper/MemeMapper.java` to lock lifecycle reads
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/notification/application/NotificationPublisher.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/notification/domain/NotificationConstants.java`
- Test: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/modules/meme/application/MemeEditRevisionReviewTest.java`

**Interfaces:**
- `MemeEditRevisionReviewService.pagePending(Integer page, Integer size): PageVO<AdminMemeEditRevisionVO>`
- `MemeEditRevisionReviewService.detail(Long revisionId): AdminMemeEditRevisionVO`
- `MemeEditRevisionReviewService.approve(Long revisionId, Long adminUserId): AdminMemeEditRevisionVO`
- `MemeEditRevisionReviewService.reject(Long revisionId, Long adminUserId, String note): AdminMemeEditRevisionVO`
- Routes: `GET /admin/v1/meme-edit-revisions`, `GET /admin/v1/meme-edit-revisions/{id}`, `PATCH /admin/v1/meme-edit-revisions/{id}`.

- [x] **Step 1: Add failing tests** for approval content replacement, rejection preserving old content, hash conflict, duplicate action, safe status preservation (`1` stays `1`, `3` stays `3`), labels/resources, unchanged counters and notification outcome.
- [x] **Step 2: Run `mvn -f just_because_meme_understands_you-backend/pom.xml -Dtest=MemeEditRevisionReviewTest test` and confirm failures.**
- [x] **Step 3: Implement revision paging/detail and transactional approval/rejection.** Compare canonical content hash before applying; lock the meme and revision rows; preserve identity, release time and counters; replace resource/tag relations and update tag counts by set difference.
- [x] **Step 4: Ensure OSS cleanup only runs after commit and never removes URLs reused by the approved version.** On rejection, clean only objects exclusively introduced by the rejected revision.
- [x] **Step 5: Add idempotent review notifications** with the revision/meme as target and include rejection notes for the author.
- [x] **Step 6: Run `mvn -f just_because_meme_understands_you-backend/pom.xml -Dtest=MemeEditRevisionReviewTest test` and confirm all cases pass.**

**Task 3 implementation note:** Review uses direct `BaseMapper` operations for revisions, tags and resources. Final review added `MemeMapper.selectByIdForUpdate` so lifecycle status writes serialize with content approval.

### Task 4: 用户端编辑表单与审核状态展示

**Files:**
- Modify: `Just_because_meme_understands_you-frontend/src/api/meme.js`
- Modify: `Just_because_meme_understands_you-frontend/src/views/UserProfile.vue`
- Modify: `Just_because_meme_understands_you-frontend/src/views/PublishMeme.vue`
- Create: `Just_because_meme_understands_you-frontend/src/utils/memeEditForm.mjs`
- Test: `Just_because_meme_understands_you-frontend/tests/memeEditForm.test.mjs`
- Modify: `Just_because_meme_understands_you-frontend/src/router/index.js` only if a dedicated edit route is needed; prefer the existing `publishMeme` route with an `edit` query parameter.
- Test: `npm run lint` and `npm run build` in `Just_because_meme_understands_you-frontend`; verify route/form rendering in the existing browser if the local app is available.

**Interfaces:**
- `submitMemeEdit(memeId, payload): Promise<MemeEditRevisionVO>` sends a complete editable snapshot using `PUT /memes/{memeId}`.
- `PublishMeme.vue` resolves `route.query.edit`, calls `getMemeDetail`, maps detail fields into the shared form, and submits through `submitMemeEdit`.
- Personal list item fields: `editRevisionStatus`, `editRevisionCreatedAt`, `editRevisionNote`.

- [x] **Step 1: Verify the API helper** sends `PUT /memes/{id}`, only editable fields, and surfaces server business errors; add pure mapping tests without new dependencies.
- [x] **Step 2: Add “修改内容” to the owner card menu for statuses `1` and `3`; show pending status and rejection reason.**
- [x] **Step 3: Implement edit mode in the existing publish page**, preloading title, introduction, cover, tags, media and links; preserve form values after submission errors.
- [x] **Step 4: Show the confirmation copy from the spec and return to the owner’s published list after successful submission.**
- [x] **Step 5: Run frontend lint/build and inspect for regressions.**

### Task 5: 管理端修改审核队列

**Files:**
- Create: `just_because_meme_understands_you-admin/src/api/adminMemeEditRevision.js`
- Create: `just_because_meme_understands_you-admin/src/views/memes/MemeEditRevisionQueue.vue`
- Modify: `just_because_meme_understands_you-admin/src/router/index.js`
- Modify: `just_because_meme_understands_you-admin/src/layouts/AdminLayout.vue`
- Test: admin frontend lint/build; API behavior verified by backend integration tests.

**Interfaces:**
- Admin API module exports `fetchAdminMemeEditRevisions(params)`, `fetchAdminMemeEditRevision(id)`, `approveAdminMemeEditRevision(id)`, and `rejectAdminMemeEditRevision(id, note)`.
- `MemeEditRevisionQueue.vue` shows paged pending revisions, original and modified fields, author, submitted time, approve action, and reject action requiring a non-empty note.

- [x] **Step 1: Add the API module with exact route and response handling.**
- [x] **Step 2: Add a focused queue component** and expose it as a separate management route and navigation entry without changing first-publication moderation behavior.
- [x] **Step 3: Add a review detail comparison** for text, cover, tags, media and links; refresh the pending queue after a successful decision.
- [x] **Step 4: Run admin lint/build and verify rejection cannot be submitted without a reason.**

### Task 6: 集成验证与文档收尾

**Files:**
- Modify: `docs/superpowers/plans/2026-10-10-published-meme-edit.md` to mark completed steps.
- Modify: `docs/superpowers/specs/2026-10-10-published-meme-edit-design.md` only if implementation uncovers a necessary design correction.
- Verify: backend focused tests/full relevant suite, user frontend lint/build, admin frontend lint/build, SQL migration contract.

- [x] **Step 1: Run the backend suite and record the result.** Final run of `mvn -f just_because_meme_understands_you-backend/pom.xml clean test "-Dtest=!JustBecauseMemeUnderstandsYouApplicationTests"` passed 209 tests (1 skipped). The context-load test was excluded because its `dev` profile connects to the configured MySQL database and automatically runs Flyway migrations; this verification must not mutate that external database.
- [x] **Step 2: Run user frontend lint/build and admin frontend lint/build.** User frontend: `npx vue-cli-service lint src --ext .js,.vue,.mjs`, build and the two `memeEditForm` Node tests passed. Admin frontend lint and build passed. The user frontend's default lint script references a missing `tests` path, so lint was run against `src` explicitly.
- [x] **Step 3: Review the feature diff against the spec**, preserving existing unrelated workspace modifications.
- [x] **Step 4: Record verification results and remaining limitations in the plan.** Reuse the existing tracked `V2026101002` revision-table migration; do not duplicate it at a later version. A separate uncommitted user-soft-delete migration also uses `V2026101002`, so the workspace still has a pre-existing Flyway version collision that must be reconciled with its database history before applying that unrelated migration. Resource status `2` archives replaced rows; the checked-in schema comment documents it, while the tinyint column needs no shape migration.
