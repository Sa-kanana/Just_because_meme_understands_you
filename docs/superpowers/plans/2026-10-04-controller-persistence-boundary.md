# Controller Persistence Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move meme-tag lookup and API VO mapping out of `MemeController` while preserving the existing endpoint contract.

**Architecture:** Replace the unused generic `IMemeTagService extends IService<MemeTag>` surface with an explicit `listTagOptions()` application query. `MemeTagServiceImpl` will use `MemeTagMapper` internally and return the same `MemeTagVO` values; the controller will depend only on the application service. A JUnit source-boundary test will prevent API controllers from importing domain entities or persistence mappers.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, JUnit 5, Mockito, Spring Test.

**Spec:** `docs/superpowers/specs/2026-10-04-controller-persistence-boundary-design.md`

## Global Constraints

- `GET /memes/tags` path, HTTP method and unified response wrapper remain unchanged.
- Tag response remains `Result<List<MemeTagVO>>`, with `id`, `name` fields and query-order semantics unchanged.
- Do not change business rules, authentication or exception handling, database configuration, the `dev` profile, or other endpoints.
- Preserve all existing uncommitted workspace changes; do not reset, reformat, or clean up unrelated code.
- Add no third-party dependency; do not migrate module DTO/VO dependencies in this task.
- Backend context tests use the `dev` profile and connect to the configured MySQL and Redis services.

## Review Focus

- A mapper returning `null` must still produce an empty tag list; test: `listTagOptionsReturnsEmptyWhenMapperReturnsNull`.
- An empty tag table must produce an empty tag list; test: `listTagOptionsReturnsEmptyWhenMapperReturnsEmpty`.
- Tag `id` and `name` values, including nullable fields, must be copied unchanged; test: `listTagOptionsMapsIdAndNameInOrder`.
- Mapper ordering and repeated rows must be preserved; test: `listTagOptionsMapsIdAndNameInOrder` with repeated entries.
- The endpoint path and success envelope must remain stable; test: `listTagsKeepsRouteAndSuccessEnvelope`.

---

### Task 1: Move tag options behind the meme application service

**Files:**
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/application/IMemeTagService.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/application/impl/MemeTagServiceImpl.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/controller/MemeController.java`
- Modify: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/controller/HomeController.java`
- Create: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/architecture/ApiControllerDependencyTests.java`
- Create: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/modules/meme/application/impl/MemeTagServiceImplTest.java`
- Create: `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/api/MemeTagControllerTest.java`
- Delete the corresponding tracked legacy paths when committing these moved files: `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/controller/MemeController.java`, `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/controller/HomeController.java`, `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/service/meme/IMemeTagService.java`, `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/service/meme/impl/MemeTagServiceImpl.java`.

**Interfaces:**
- Consumes: `MemeTagMapper.selectList(null)` and its `List<MemeTag>` result.
- Produces: `IMemeTagService.listTagOptions(): List<MemeTagVO>`; each result copies `id` and `name`, preserving input order and duplicates.

- [ ] **Step 1: Write `ApiControllerDependencyTests`**

Scan `api/user/controller` and `api/admin/controller`; fail if a controller imports `modules.*.domain.entity` or `modules.*.infrastructure.persistence.mapper`.

- [ ] **Step 2: Run the boundary test and verify it fails on the current imports**

Run from `just_because_meme_understands_you-backend`: `./mvnw.cmd -Dtest=ApiControllerDependencyTests test`.
Expected: FAIL identifying the entity/mapper imports in the existing controllers.

- [ ] **Step 3: Write `MemeTagServiceImplTest`**

Mock `MemeTagMapper` and add `listTagOptionsReturnsEmptyWhenMapperReturnsNull`, `listTagOptionsReturnsEmptyWhenMapperReturnsEmpty`, and `listTagOptionsMapsIdAndNameInOrder`. The final case must assert a nullable ID/name, order, and a repeated row are copied unchanged.

- [ ] **Step 4: Run the service test and verify it fails before implementation**

Run: `./mvnw.cmd -Dtest=MemeTagServiceImplTest test`.
Expected: FAIL because `listTagOptions()` is not implemented.

- [ ] **Step 5: Write `MemeTagControllerTest`**

Add `listTagsKeepsRouteAndSuccessEnvelope`: assert the method is mapped to `GET /memes/tags`, the result uses the existing success code/message, the returned data is the service result, and `listTagOptions()` is called once.

- [ ] **Step 6: Run the controller test and verify it fails before implementation**

Run: `./mvnw.cmd -Dtest=MemeTagControllerTest test`.
Expected: FAIL because `MemeController` does not yet depend on `IMemeTagService.listTagOptions()`.

- [ ] **Step 7: Implement the explicit application query**

Make `IMemeTagService` a focused interface with `List<MemeTagVO> listTagOptions()` instead of extending MyBatis-Plus `IService<MemeTag>`. Implement it in `MemeTagServiceImpl` with constructor-injected `MemeTagMapper`; use the existing `selectList(null)` query and map only `id`/`name`, returning `List.of()` for a `null` or empty mapper result. Inject `IMemeTagService` into `MemeController` and remove its mapper/entity imports and inline mapping. Remove the unused `Meme` import from `HomeController`.

- [ ] **Step 8: Run focused tests and verify they pass**

Run: `./mvnw.cmd -Dtest=ApiControllerDependencyTests,MemeTagServiceImplTest,MemeTagControllerTest test`.
Expected: BUILD SUCCESS; mapping, endpoint contract, and Controller boundary tests pass.

- [ ] **Step 9: Run the full backend test suite**

Run: `./mvnw.cmd test`.
Expected: BUILD SUCCESS, including the Spring context test. It will connect to configured MySQL/Redis under `dev`.

- [ ] **Step 10: Review and commit only this task's files**

From the repository root, stage only these paths (the plan and `docs/` are ignored by `.gitignore`):

```powershell
git add -f -- `
  docs/superpowers/plans/2026-10-04-controller-persistence-boundary.md `
  just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/application/IMemeTagService.java `
  just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/modules/meme/application/impl/MemeTagServiceImpl.java `
  just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/controller/MemeController.java `
  just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/api/user/controller/HomeController.java `
  just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/architecture/ApiControllerDependencyTests.java `
  just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/modules/meme/application/impl/MemeTagServiceImplTest.java `
  just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/api/MemeTagControllerTest.java `
  just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/controller/MemeController.java `
  just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/controller/HomeController.java `
  just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/service/meme/IMemeTagService.java `
  just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/service/meme/impl/MemeTagServiceImpl.java
git diff --cached --name-status
git diff --cached --check
```

Abort if the staged list contains any unrelated existing change; otherwise commit as `refactor: move meme tag lookup behind service`.
