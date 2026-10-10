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
- 作者修改封面、标签或资源失败时表单仍保留原输入，详情页/个人列表不误显示未批准内容；由 Task 4 的前端流程验证覆盖。

---

### Task 1: 修改稿数据模型与迁移

**Files:**
- Create: `just_because_meme_understands_you-backend/src/main/resources/db/migration/V2026101002__meme_edit_revisions.sql`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/domain/entity/MemeEditRevision.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/infrastructure/persistence/mapper/MemeEditRevisionMapper.java`
- Test: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/db/MemeEditRevisionSchemaContractTest.java`

**Interfaces:**
- Produces: `MemeEditRevisionMapper extends BaseMapper<MemeEditRevision>` and persisted fields defined in the spec.
- `content_json` stores the complete editable payload; statuses are `PENDING`, `APPROVED`, `REJECTED`, `CANCELED`.

- [ ] **Step 1: Write schema contract tests** asserting all required columns and Chinese comments exist, status defaults to `PENDING`, and indexes cover `(meme_id, status)` and `(status, created_at, id)`.
- [ ] **Step 2: Run `mvn -f just_because_meme_understands_you-backend/pom.xml -Dtest=MemeEditRevisionSchemaContractTest test` and confirm it fails** because the migration resource does not exist.
- [ ] **Step 3: Add the migration, entity, and mapper**. Include a Chinese `COMMENT` for every SQL column and Chinese field comments on the entity. Keep completed revision rows for audit; do not add a partial unique index.
- [ ] **Step 4: Run `mvn -f just_because_meme_understands_you-backend/pom.xml -Dtest=MemeEditRevisionSchemaContractTest test` and confirm it passes.**

### Task 2: 用户提交修改稿与个人主页状态

**Files:**
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/dto/MemeEditRequestDTO.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/vo/MemeEditRevisionVO.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/application/MemeEditRevisionService.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/controller/MemeController.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/vo/UserPublishedMemeVO.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/user/application/profile/impl/UserProfileServiceImpl.java`
- Test: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/modules/meme/application/MemeEditRevisionSubmitTest.java`

**Interfaces:**
- `MemeEditRevisionService.submitEdit(Long userId, Long memeId, MemeEditRequestDTO request): MemeEditRevisionVO`
- `MemeEditRevisionService.getLatestRevisionStatuses(List<Long> memeIds): Map<Long, MemeEditRevisionVO>`
- `MemeController.updateOwnMeme(String memeId, MemeEditRequestDTO body, HttpServletRequest servletRequest): Result<MemeEditRevisionVO>` at `PUT /v1/memes/{memeId}`.
- `UserPublishedMemeVO` adds the latest edit revision status, submission time, and review note when the requester is the owner.

- [ ] **Step 1: Add failing service tests** for author submission on status `1`/`3`, non-owner rejection, other status rejection, invalid tags/resources, and rejection of an existing `PENDING` revision.
- [ ] **Step 2: Run `mvn -f just_because_meme_understands_you-backend/pom.xml -Dtest=MemeEditRevisionSubmitTest test` and confirm the expected failures.**
- [ ] **Step 3: Implement owner/status validation, normalized JSON snapshot and deterministic base-content SHA-256.** Lock the parent meme row in the transaction before checking for a pending revision and inserting one.
- [ ] **Step 4: Expose `PUT /v1/memes/{memeId}` and owner-only latest revision status in the user's published meme page response.** The request DTO contains only editable content fields.
- [ ] **Step 5: Run `mvn -f just_because_meme_understands_you-backend/pom.xml -Dtest=MemeEditRevisionSubmitTest test` and confirm all cases pass.**

### Task 3: 管理端审核与正式内容应用

**Files:**
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/admin/controller/AdminMemeEditRevisionController.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/admin/dto/AdminMemeEditReviewRequestDTO.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/admin/vo/AdminMemeEditRevisionVO.java`
- Create: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/application/MemeEditRevisionReviewService.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/infrastructure/persistence/mapper/MemeMapper.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/infrastructure/persistence/mapper/MemeResourceMapper.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/infrastructure/persistence/mapper/MemeTagRelationMapper.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/notification/application/NotificationPublisher.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/notification/domain/NotificationConstants.java`
- Test: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/modules/meme/application/MemeEditRevisionReviewTest.java`

**Interfaces:**
- `MemeEditRevisionReviewService.pagePending(Integer page, Integer size): PageVO<AdminMemeEditRevisionVO>`
- `MemeEditRevisionReviewService.detail(Long revisionId): AdminMemeEditRevisionVO`
- `MemeEditRevisionReviewService.approve(Long revisionId, Long adminUserId): AdminMemeEditRevisionVO`
- `MemeEditRevisionReviewService.reject(Long revisionId, Long adminUserId, String note): AdminMemeEditRevisionVO`
- Routes: `GET /admin/v1/meme-edit-revisions`, `GET /admin/v1/meme-edit-revisions/{id}`, `PATCH /admin/v1/meme-edit-revisions/{id}`.

- [ ] **Step 1: Add failing tests** for approval content replacement, rejection preserving old content, hash conflict, duplicate action, safe status preservation (`1` stays `1`, `3` stays `3`), labels/resources, unchanged counters and notification outcome.
- [ ] **Step 2: Run `mvn -f just_because_meme_understands_you-backend/pom.xml -Dtest=MemeEditRevisionReviewTest test` and confirm failures.**
- [ ] **Step 3: Implement revision paging/detail and transactional approval/rejection.** Compare canonical content hash before applying; lock the meme and revision rows; preserve identity, release time and counters; replace resource/tag relations and update tag counts by set difference.
- [ ] **Step 4: Ensure OSS cleanup only runs after commit and never removes URLs reused by the approved version.** On rejection, clean only objects exclusively introduced by the rejected revision.
- [ ] **Step 5: Add idempotent review notifications** with the revision/meme as target and include rejection notes for the author.
- [ ] **Step 6: Run `mvn -f just_because_meme_understands_you-backend/pom.xml -Dtest=MemeEditRevisionReviewTest test` and confirm all cases pass.**

### Task 4: 用户端编辑表单与审核状态展示

**Files:**
- Modify: `Just_because_meme_understands_you-frontend/src/api/meme.js`
- Modify: `Just_because_meme_understands_you-frontend/src/views/UserProfile.vue`
- Modify: `Just_because_meme_understands_you-frontend/src/views/PublishMeme.vue`
- Modify: `Just_because_meme_understands_you-frontend/src/router/index.js` only if a dedicated edit route is needed; prefer the existing `publishMeme` route with an `edit` query parameter.
- Test: `npm run lint` and `npm run build` in `Just_because_meme_understands_you-frontend`; verify route/form rendering in the existing browser if the local app is available.

**Interfaces:**
- `submitMemeEdit(memeId, payload): Promise<MemeEditRevisionVO>` sends a complete editable snapshot using `PUT /memes/{memeId}`.
- `PublishMeme.vue` resolves `route.query.edit`, calls `getMemeDetail`, maps detail fields into the shared form, and submits through `submitMemeEdit`.
- Personal list item fields: `editRevisionStatus`, `editRevisionCreatedAt`, `editRevisionNote`.

- [ ] **Step 1: Verify the API helper** sends `PUT /memes/{id}`, only editable fields, and surfaces server business errors; the frontend package has no unit-test runner, so use lint/build and browser verification instead of adding a dependency.
- [ ] **Step 2: Add “编辑” to the owner card menu for statuses `1` and `3`; show pending status and rejection reason.**
- [ ] **Step 3: Implement edit mode in the existing publish page**, preloading title, introduction, cover, tags, media and links; preserve form values after submission errors.
- [ ] **Step 4: Show the confirmation copy from the spec and return to the owner’s published list after successful submission.**
- [ ] **Step 5: Run frontend lint/build and inspect for regressions.**

### Task 5: 管理端修改审核队列

**Files:**
- Create: `just_because_meme_understands_you-admin/src/api/adminMemeEditRevision.js`
- Create: `just_because_meme_understands_you-admin/src/views/memes/MemeEditRevisionQueue.vue`
- Modify: `just_because_meme_understands_you-admin/src/views/memes/MemeListView.vue`
- Test: admin frontend lint/build; API behavior verified by backend integration tests.

**Interfaces:**
- Admin API module exports `fetchAdminMemeEditRevisions(params)`, `fetchAdminMemeEditRevision(id)`, `approveAdminMemeEditRevision(id)`, and `rejectAdminMemeEditRevision(id, note)`.
- `MemeEditRevisionQueue.vue` shows paged pending revisions, original and modified fields, author, submitted time, approve action, and reject action requiring a non-empty note.

- [ ] **Step 1: Add the API module with exact route and response handling.**
- [ ] **Step 2: Add a focused queue component** and connect it as a tab in the existing meme management view without changing first-publication moderation behavior.
- [ ] **Step 3: Add a review detail comparison** for text, cover, tags, media and links; refresh the pending queue after a successful decision.
- [ ] **Step 4: Run admin lint/build and verify rejection cannot be submitted without a reason.**

### Task 6: 集成验证与文档收尾

**Files:**
- Modify: `docs/superpowers/plans/2026-10-10-published-meme-edit.md` to mark completed steps.
- Modify: `docs/superpowers/specs/2026-10-10-published-meme-edit-design.md` only if implementation uncovers a necessary design correction.
- Verify: backend focused tests/full relevant suite, user frontend lint/build, admin frontend lint/build, SQL migration contract.

- [ ] **Step 1: Run `mvn -f just_because_meme_understands_you-backend/pom.xml test` and record the full backend suite result.**
- [ ] **Step 2: Run user frontend lint/build and admin frontend lint/build.**
- [ ] **Step 3: Review the final diff against the spec, checking that pre-existing workspace modifications remain intact and no unrelated files changed.**
- [ ] **Step 4: Record verification results and remaining limitations in the plan.**
