# OSS 文件资产管理实施计划

> **执行要求：** 实施时逐任务使用 `superpowers:executing-plans`，按复选框逐项推进；编码前先遵循测试驱动流程。

**目标：** 为图片建立从服务端签发上传票据、业务引用、延迟删除、失败重试到 dry-run 对账的完整生命周期。

**架构：** 业务字段继续保存 objectKey，读取接口继续返回原有访问 URL。新增 OSS 资产模块、Flyway 元数据表、服务端生成的上传票据、事务内引用维护、MySQL 删除任务队列、定时 Worker 和可重入的历史回填器。浏览器仍直接上传阿里云 OSS；不引入消息队列。

**技术栈：** Spring Boot、Java、MyBatis-Plus、MySQL 8、Flyway、阿里云 OSS SDK、Vue 3、Axios、JUnit 5、Mockito、npm。

**设计稿：** `docs/superpowers/specs/2026-10-11-oss-file-asset-management-design.md`

**路径说明：** 下文中未写仓库前缀的后端 Java 路径，均相对 `just_because_meme_understands_you-backend/src/main/java/com/sakana/just_because_meme_understands_you/`；后端测试路径相对 `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/`。

## 全局约束

- 现有业务图片字段继续存 objectKey；外部 URL 保持原样。
- 不引入 MQ、SHA-256 去重、管理端资产浏览页面，也不由应用自动修改 Bucket 生命周期规则。
- 临时上传前缀为 `tmp/`；建议 OSS 生命周期 2 天过期；业务删除延迟 2 天。
- 删除失败按 1 分钟、5 分钟、30 分钟、2 小时、6 小时重试，最多 5 次。
- 孤儿对账每天运行；首次发现后至少间隔 7 天复核；默认 dry-run。
- 新增 SQL 列必须有中文 `COMMENT`；迁移复制字段时逐项注明来源和目标。
- 新增配置项、DTO 和实体字段必须带清晰中文注释。
- 不改变现有审核、状态、缓存、标签计数和索引行为；在原有事务中维护资产引用。
- 历史引用回填完成前，删除 Worker 必须暂停所有物理删除；回填未完成时关闭对账 dry-run 也不得生成删除任务。
- `PENDING_DELETE` 资产在对象删除开始前重新获得引用时，恢复为 `ACTIVE` 并取消等待任务；进入 `DELETING` 或 `DELETED` 后禁止重新绑定。

## 重点审阅场景

- 客户端伪造扩展名、MIME、文件大小或未授权场景时，签名和资产记录都不得创建；在任务 2 覆盖。
- 上传未确认、已过期、所有者不匹配或已经进入删除流程时，不得绑定业务；在任务 2、4 覆盖。
- 多条业务记录共享同一对象时，只删除其中一条引用不得删除 OSS 对象；在任务 4 覆盖。
- 拒绝审核稿时，如果图片同时被正式梗引用，只释放审核稿引用；在任务 7 覆盖。
- OSS 列举或删除失败，以及对象落在保护前缀时，保存可恢复的任务或候选状态且不得误删；在任务 4、8 覆盖。

---

### Task 1：建立资产表结构和持久化模型

**文件：**

- 新建 `just_because_meme_understands_you-backend/src/main/resources/db/migration/V2026101101__file_asset_lifecycle.sql`
- 新建 `modules/oss/domain/FileAssetStatus.java`
- 新建 `modules/oss/domain/entity/FileAsset.java`、`FileAssetReference.java`、`FileDeleteTask.java`、`FileOrphanCandidate.java`、`FileAssetBackfillState.java`
- 新建 `modules/oss/infrastructure/persistence/mapper/FileAssetMapper.java`、`FileAssetReferenceMapper.java`、`FileDeleteTaskMapper.java`、`FileOrphanCandidateMapper.java`、`FileAssetBackfillStateMapper.java`
- 修改 `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/db/FlywayMigrationCatalogTest.java`
- 新建 `just_because_meme_understands_you-backend/src/test/java/com/sakana/just_because_meme_understands_you/modules/oss/infrastructure/persistence/FileAssetSchemaContractTest.java`

**接口与数据约定：**

- 资产状态：`TEMP`、`ACTIVE`、`PENDING_DELETE`、`DELETING`、`DELETED`；实体沿用项目的 MyBatis-Plus `BaseMapper` 模式。
- 迁移建立 `file_asset`、`file_asset_ref`、`file_delete_task`、`file_orphan_candidate`、`file_asset_backfill_state` 五张表。引用表对资产表设置外键；业务 ID 保持多态逻辑引用。
- `FileAssetMapper.selectByObjectKeyForUpdate(String objectKey)` 锁定资产；引用 Mapper 提供引用计数和业务字段查找；删除任务 Mapper 提供带租约的到期任务领取。
- 对象 key 唯一；同一业务字段只允许一个当前资源；状态和重试查询建立匹配索引。所有列及索引用途用中文注释说明。

**步骤：**

- [x] 1. 先写结构契约测试，检查五张表、唯一约束、业务字段唯一约束、外键、状态/重试索引和每列中文注释。
- [x] 2. 在后端运行 `.\mvnw.cmd -Dtest=FileAssetSchemaContractTest test`，确认迁移资源缺失导致测试失败。
- [x] 3. 添加 Flyway 迁移、实体和 Mapper；遵循项目的信息架构检查写法，并在复制历史字段时逐项说明来源与目标。
- [x] 4. 注册迁移后运行 `.\mvnw.cmd "-Dtest=FileAssetSchemaContractTest,FlywayMigrationCatalogTest" test`，预期通过。
- [x] 5. 提交：`feat: add file asset lifecycle schema`。

### Task 2：签发并确认安全上传票据

**文件：**

- 新建 `api/user/dto/OssUploadTicketRequestDTO.java`
- 新建 `modules/oss/application/FileAssetUploadConfirmationService.java`
- 修改 `modules/oss/application/OssUploadPolicyService.java`
- 修改 `api/user/controller/OssController.java`、`api/user/vo/OssUploadPolicyVO.java`
- 新建 `modules/oss/application/OssUploadTicketServiceTest.java`
- 修改 `api/RestfulApiRouteContractTest.java`

**接口：**

- `OssUploadPolicyService.createTicket(OssUploadTicketRequestDTO request, HttpServletRequest httpRequest): OssUploadPolicyVO` 创建 `TEMP` 资产，并返回 `assetId`、确切 `objectKey`、签名策略、主机、公开 URL 前缀和过期时间。
- `FileAssetUploadConfirmationService.confirmUpload(Long ownerId, Long assetId): void` 检查所有者、OSS 对象存在性、大小、MIME、有效期和 `TEMP` 状态，然后记录确认时间。
- 新路由为 `POST /v1/uploads/tickets` 和 `POST /v1/uploads/{assetId}/confirm`；移除仅按前缀签名的旧 `GET /v1/uploads/policies` 路由，避免绕过资产登记。

**步骤：**

- [x] 1. 先为合法图片、非法 MIME/扩展名、超过 10 MiB、未授权首页场景、所有者不匹配、OSS 对象缺失、元数据不匹配和重复确认编写测试。
- [x] 2. 运行 `.\mvnw.cmd "-Dtest=OssUploadTicketServiceTest,RestfulApiRouteContractTest" test`，确认新流程尚未实现时失败。
- [x] 3. 实现服务端 UUID key：`tmp/{scene}/{userId}/{yyyyMMdd}/{uuid}.{ext}`；仅允许精确 key、声明 MIME 和大小范围；签名有效期 60 秒，文件上限 10 MiB。
- [x] 4. 运行聚焦测试，确认路由、权限和校验通过。
- [x] 5. 提交：`feat: issue tracked OSS upload tickets`。

### Task 3：更新用户端和管理端直传工具

**文件：**

- 修改 `just_because_meme_understands_you-frontend/src/api/oss.js`
- 修改 `just_because_meme_understands_you-admin/src/api/oss.js`
- 验证两个项目 `package.json` 中现有的 build 和 lint 脚本

**接口约定：**

- 两端 `uploadToOss(file, fileType, fileName)` 继续返回公开 URL 字符串，避免改动现有头像、梗、评论和首页表单及业务请求 DTO。
- 客户端先请求 `/uploads/tickets`，严格使用服务端返回的 objectKey 直传 OSS，上传成功后请求确认接口；上传失败不确认。

**步骤：**

- [x] 1. 检查前后端依赖，没有现成 JS 单元测试框架；不为此引入测试依赖。验证方式使用现有 lint/build，并用可执行的手工流程检查票据、直传和确认顺序。
- [x] 2. 更新两端上传工具，移除客户端自行生成 objectKey 的逻辑；保留浏览器 MIME、扩展名和 10 MiB 校验及直传行为。
- [x] 3. 在两个前端目录分别运行 `npm run lint` 和 `npm run build`，预期通过。
- [x] 4. 提交：`feat: confirm browser OSS uploads`。

### Task 4：实现引用管理、延迟删除队列和 OSS Worker

**文件：**

- 新建 `modules/oss/application/FileAssetService.java`、`FileAssetDeleteWorker.java`、`OssCleanupProperties.java`
- 修改 `modules/oss/application/OssObjectPromoteService.java`、`src/main/resources/application.yml`
- 新建 `modules/oss/application/FileAssetServiceTest.java`、`FileAssetDeleteWorkerTest.java`

**接口：**

- `FileAssetService.promote(String keyOrUrl, Long uploaderId, String scene): String` 归一化 URL/key，验证临时资产的所有者和确认状态，执行现有按场景复制操作，并更新资产 key/状态。
- `replaceReference(String objectKeyOrUrl, String bizType, Long bizId, String fieldKey, String reason)` 在同一事务中绑定新对象并释放该业务字段的旧引用；若新对象处于 `PENDING_DELETE`，先恢复为 `ACTIVE` 并取消其等待任务；`DELETING`、`DELETED` 对象拒绝重新绑定。
- `releaseReference(String bizType, Long bizId, String fieldKey, String reason)` 删除一个引用；仅最后一个引用释放时才把资产改为 `PENDING_DELETE` 并创建延迟任务。
- `FileAssetDeleteWorker.processDueTasks(int limit): int` 有界领取到期任务，重查引用和前缀后执行幂等删除并更新重试状态。
- 默认配置：批次 100、轮询 60 秒、删除等待 2 天、最多 5 次、孤儿对账 dry-run 开启；每个配置项有中文注释。

**步骤：**

- [x] 1. 先写测试覆盖临时资产所有者/确认校验、共享资产、同字段替换、最后引用延迟入队、待删除资产重绑并取消任务、回填未完成时阻止物理删除、非法/保护 key、dry-run、成功删除、重复领取和重试间隔/上限。
- [x] 2. 运行 `.\mvnw.cmd "-Dtest=FileAssetServiceTest,FileAssetDeleteWorkerTest" test`，确认核心实现缺失时失败。
- [x] 3. 实现事务内引用变更及有租约、有界的数据库 Worker。OSS 网络调用放在领取事务之外；外部删除前将资产置为 `DELETING`，阻止并发重新绑定。历史回填未完成时暂停 Worker 的 OSS 删除；删除失败且仍可重试时，将资产恢复为 `PENDING_DELETE` 并按退避时间重新排队。
- [x] 4. 保留安全的 promote/copy；在后续业务迁移完成后移除 `OssObjectPromoteService` 中的直接删除入口。
- [x] 5. 运行聚焦测试，确认共享/保护对象不被删除且重试有上限。
- [x] 6. 提交：`feat: queue safe OSS asset deletion`。

### Task 5：回填历史资产并清理过期临时文件

**文件：**

- 新建 `modules/oss/application/FileAssetLegacyBackfillRunner.java`
- 新建 `modules/oss/infrastructure/persistence/mapper/FileAssetLegacyReferenceMapper.java`
- 新建 `modules/oss/infrastructure/task/TempFileAssetCleanupTask.java`
- 新建 `modules/oss/application/FileAssetLegacyBackfillRunnerTest.java`
- 使用现有 `db/FlywayMigrationIntegrationTest.java` 验证迁移

**接口与来源映射：**

- 回填 `user.avatar`、`meme.image`、`meme_resource.resource_url`、`meme_comment_images.url`、`home_image.img_url` 和待审核 `meme_edit_revision.content_json` 中的封面/资源。
- 业务字段映射为 `USER/AVATAR`、`MEME/COVER`、`MEME_RESOURCE/URL`、`COMMENT_IMAGE/URL`、`BANNER/IMAGE`、`MEME_REVISION/COVER|RESOURCE:{index}`。
- 仅将配置 OSS/CDN 域名归一化为自有 key；跳过外链、非本 Bucket URL 和保护前缀。`expireUnconfirmedAssets(LocalDateTime cutoff): int` 将超过 3 天且仍存在的临时对象加入可靠删除队列；成功删除后保留 `DELETED` 记录。

**步骤：**

- [ ] 1. 先测试 key 归一化、OSS/CDN 完整 URL、外链跳过、重复 key 去重、审核稿引用、断点续跑、并发启动只允许一个回填租约、过期临时资产入队和回填完成门禁。
- [ ] 2. 运行 `.\mvnw.cmd -Dtest=FileAssetLegacyBackfillRunnerTest test`，确认尚未实现时失败。
- [ ] 3. 实现分批、幂等回填；使用 `file_asset_backfill_state` 的数据库租约确保多实例只有一个执行者，并保存检查点，仅在批次完成后提交；每种旧字段到新引用的映射都用中文说明。回填结束前，禁用 Worker 物理删除和对账删除任务创建；回填完成状态持久化后才开放删除流程。
- [ ] 4. 添加每日过期 TEMP 扫描，不物理删除资产审计记录。
- [ ] 5. 运行聚焦测试及 `.\mvnw.cmd -Dtest=FlywayMigrationIntegrationTest test`；未设置 `MIGRATION_TEST_MYSQL_URL` 时，集成测试可按现有规则跳过。
- [ ] 6. 提交：`feat: backfill legacy OSS asset references`。

### Task 6：接入头像、首页图和评论图片引用

**文件：**

- 修改 `modules/user/application/profile/impl/UserProfileServiceImpl.java`
- 修改 `modules/admin/application/impl/AdminHomeImageServiceImpl.java`
- 修改 `modules/comment/application/support/MemeCommentSupport.java`
- 修改现有 `modules/admin/application/impl/AdminHomeImageDeletionTest.java`、`modules/user/application/profile/UserProfileServiceStatsTest.java`
- 新建 `modules/oss/application/FileAssetBusinessReferenceTest.java`

**引用规则：**

- 头像：用户行更新事务中调用 `replaceReference(newKey, "USER", userId, "AVATAR", "AVATAR_REPLACED")`；直接 multipart 上传和 OSS 直传都登记资产。
- 首页图：使用 `BANNER/{homeImageId}/IMAGE`；插入后绑定，更新时替换，删除时只释放引用。
- 评论图片：按 `meme_comment_images` 行 ID 使用 `COMMENT_IMAGE/{commentImageRowId}/URL`；评论软删除后图片行仍保留，因此继续保留引用；若存在硬删除，则一并释放。

**步骤：**

- [ ] 1. 先测试新图绑定后再解绑旧图，以及删除首页图不再同步调用 OSS。
- [ ] 2. 运行 `.\mvnw.cmd "-Dtest=AdminHomeImageDeletionTest,UserProfileServiceStatsTest,FileAssetBusinessReferenceTest" test`，确认原直接删除路径/遗漏引用的断言失败。
- [ ] 3. 将业务事务接入 `FileAssetService`，保持现有 URL 返回、缓存清理和软删除语义。
- [ ] 4. 运行聚焦测试，确认业务事务没有 OSS 网络调用。
- [ ] 5. 提交：`feat: track profile and comment image references`。

### Task 7：接入梗、资源、审核稿和永久清理流程

**文件：**

- 修改 `modules/meme/application/MemePublishService.java`、`MemeEditRevisionService.java`、`MemeEditRevisionReviewService.java`、`MemeDeleteService.java`、`MemeOssCleanupHandler.java`
- 修改现有 `modules/meme/application/MemeDeleteServiceAdminTest.java`、`MemeEditRevisionReviewTest.java`

**引用规则：**

- 发布梗：梗 ID 创建后绑定 `MEME/{memeId}/COVER`；资源行插入后绑定 `MEME_RESOURCE/{resourceId}/URL`。外链不登记为资产。
- 编辑稿：使用 `MEME_REVISION/{revisionId}/COVER` 和 `RESOURCE:{index}`；审核通过时把引用转到正式梗/资源，拒绝、取消或替换时仅释放编辑稿引用。
- 永久清理：在清理事务内释放 `MEME/COVER` 和全部 `MEME_RESOURCE/URL` 引用。下架、锁定和仍可恢复的梗保留引用。所有调用迁移后删除 `MemeOssCleanupHandler` 直接异步删除路径。

**步骤：**

- [ ] 1. 先测试发布绑定、共享对象、审核通过时引用迁移、拒绝/取消释放、永久清理入队和下架/恢复保留引用。
- [ ] 2. 运行 `.\mvnw.cmd "-Dtest=MemePublishServiceTest,MemeEditRevisionSubmitTest,MemeEditRevisionReviewTest,MemeDeleteServiceAdminTest" test`，确认新引用转换尚未实现时失败。
- [ ] 3. 在现有事务中实现引用转换，不改变审核、状态、缓存、标签计数和索引行为。
- [ ] 4. 删除直接 OSS 删除路径及 `deleteMemeObjectsAfterCommit`、`deleteHomeImage` 的剩余调用。
- [ ] 5. 运行聚焦测试，确认发布、审核和清理事务中不再直接删除 OSS。
- [ ] 6. 提交：`feat: track meme and revision image references`。

### Task 8：增加默认 dry-run 的孤儿对账和运维说明

**文件：**

- 新建 `modules/oss/infrastructure/task/OssAssetReconciliationTask.java`
- 修改 `modules/oss/application/OssCleanupProperties.java` 和 `src/main/resources/application.yml`
- 新建 `docs/oss/file-asset-cleanup-operations.md`
- 新建 `modules/oss/infrastructure/task/OssAssetReconciliationTaskTest.java`

**接口与安全规则：**

- `OssAssetReconciliationTask.reconcile(): ReconciliationSummary` 仅分页扫描配置的前缀，将未登记 key 记为 `DISCOVERED`，7 天后复核；只有显式关闭 dry-run 且全部安全检查通过才创建删除任务。
- 默认保护 `system/`、`homepage/static/`；可删除 key 必须属于业务前缀白名单。日志按可用信息记录 `assetId`、`objectKey`、`bizType`、`bizId`、`reason`、`taskId`、`retryCount`。
- 运维说明包含 OSS 控制台将 `tmp/` 设置为 2 天过期、3–7 天 dry-run 观察期、汇总日志判读和审核后关闭 dry-run 的操作步骤。

**步骤：**

- [ ] 1. 先测试分页、首次发现、7 天确认、重复发现、dry-run 不删除、白名单检查和保护前缀跳过。
- [ ] 2. 运行 `.\mvnw.cmd -Dtest=OssAssetReconciliationTaskTest test`，确认对账任务尚不存在时失败。
- [ ] 3. 实现分页 OSS 对账；默认配置下不执行删除，并输出每日资产状态及候选数量汇总。
- [ ] 4. 编写中文运维文档，并解释所有配置项和操作步骤。
- [ ] 5. 运行聚焦测试，确认默认 dry-run 只登记候选。
- [ ] 6. 提交：`feat: reconcile orphaned OSS objects safely`。

### Task 9：全量验证与收尾

**范围：** 检查所有后端、前端、管理端、迁移和文档变更；只在新流程改变契约时调整相关测试。

**步骤：**

- [ ] 1. 在后端运行 `.\mvnw.cmd test`；MySQL 迁移集成测试仅可在未设置 `MIGRATION_TEST_MYSQL_URL` 时按既有规则跳过。
- [ ] 2. 在用户端和管理端目录分别运行 `npm run lint`、`npm run build`。
- [ ] 3. 运行 `git diff --check` 并检索 `deleteObject`、旧 `/uploads/policies`、缺少 `COMMENT` 和无中文注释的新配置/字段；只有专用存储适配器/Worker 可以删除 OSS 对象，旧客户端路由不应残留。
- [ ] 4. 按设计稿审查最终 diff，确认没有修改线上 Bucket 生命周期、没有新增 MQ 或去重依赖。
- [ ] 5. 提交最终测试/文档修正：`test: verify OSS file asset lifecycle`。
