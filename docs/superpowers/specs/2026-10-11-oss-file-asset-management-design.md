# OSS 文件资产管理设计

## 状态

待用户审阅。本文根据 `OssUploadPolicyService`、`OssObjectPromoteService`、现有图片业务流程和附件方案整理。

## 目标与约束

将图片生命周期从“业务表保存 URL、业务代码分别清理 OSS”改为统一文件资产机制，覆盖未提交上传、业务绑定与替换、延迟删除、失败重试和孤儿对象对账。业务表现有图片字段继续保存 objectKey；外部 URL 继续原样保存，以降低数据迁移和读取路径风险。公开 VO 仍通过现有 `OssUrlHelper` 转成访问 URL。

不引入 MQ。文件删除任务由 MySQL 持久化，并由 Spring 定时 Worker 消费。OSS 生命周期策略属于 Bucket 运维配置，代码提供配置说明和前缀约定，不在应用启动时修改线上 Bucket。

## 当前项目情况

- 前端与管理端通过 `GET /v1/uploads/policies` 获取 PostObject 凭证，客户端自行生成 key 并上传到 `tmp/`。
- 梗、评论和首页图在业务写入时由 `OssObjectPromoteService` 复制到正式前缀；头像已有独立写入逻辑。
- 数据库图片字段已经以 objectKey 为主，并兼容历史完整 OSS URL；部分业务删除仍由回调或同步代码直接删除对象。
- `meme_edit_revision.content_json` 也会保存待审核封面和资源图片；这些图片必须计入引用，驳回或取消时才能安全释放。

## 数据模型

新增 Flyway 迁移，创建以下 InnoDB 表，所有列均带中文 `COMMENT`，并为状态查询与业务查找建立匹配索引：

1. `file_asset`：以唯一 `object_key` 管理文件元数据、上传者、原始文件名、MIME、大小、生命周期状态、上传确认时间、激活时间、删除期限和删除时间。状态包含 `TEMP`、`ACTIVE`、`PENDING_DELETE`、`DELETING`、`DELETED`。
2. `file_asset_ref`：保存资产到业务字段的引用，字段包含资产 ID、业务类型、业务 ID、字段键和创建时间。`field_key` 用于区分封面、头像、评论图片、资源行和审核稿中的不同位置；唯一约束保证同一个业务字段只有一个当前资源，资产 ID 有外键。
3. `file_delete_task`：作为事务内删除队列，记录资产 ID、objectKey 快照、任务状态、重试次数、下次执行时间、租约时间、完成时间和最近错误；状态包含 `WAITING`、`PROCESSING`、`DONE`、`CANCELED`、`FAILED`。
4. `file_orphan_candidate`：保存 OSS 对账发现的未登记对象、首次与最近发现时间、复查状态、删除时间及最近检查结果。
5. `file_asset_backfill_state`：用迁移名和完成状态保护一次性历史数据回填，避免多实例启动时重复扫描。

Flyway 负责建表；紧随迁移运行的幂等回填器使用 `OssUrlHelper` 按当前 Bucket/CDN 域名归一化历史 URL，再把现有自有 OSS key 从 `user.avatar`、`meme.image`、`meme_resource.resource_url`、`meme_comment_images.url`、`home_image.img_url` 写入资产及引用；待审核 `meme_edit_revision.content_json` 中的自有封面和媒体地址也写入 `MEME_REVISION` 引用。回填状态逐批提交，完成后记录 checkpoint；失败可重试且不会重复创建引用。相同 objectKey 只创建一条资产记录。外部 URL、无法归属当前 Bucket 的 URL 和系统保护资源不创建可删除资产。已有业务图片字段不改写。

## 上传、确认与业务绑定

将上传凭证签发改为上传票据流程，接口为 `POST /v1/uploads/tickets` 和 `POST /v1/uploads/{assetId}/confirm`：

1. 用户或管理员提交场景、原始文件名、声明 MIME 和文件大小；服务端校验身份、场景权限、白名单扩展名/MIME、大小上限，并生成唯一的 `tmp/{scene}/{userId}/{date}/{uuid}.{ext}` objectKey。
2. 服务端在同一流程创建 `TEMP` 资产并签发仅允许该 key、声明 MIME 和大小范围的短时 PostObject policy。客户端仍直传 OSS。
3. 客户端上传成功后调用确认接口；服务端用 OSS 元数据校验对象存在、大小及 Content-Type 后记录确认时间。未确认或未使用的资产会过期清理。
4. 用户端和管理端上传工具继续返回现有 URL 字符串，业务 API 结构暂不要求携带 assetId；业务服务把 URL 归一成 key 后，按 key、上传者和已确认状态解析资产，避免直接信任客户端提供的 assetId。

业务服务在既有数据库事务内创建/更新业务行、完成临时对象转正式前缀、更新资产 key/status 并维护引用。处理头像/封面替换时，先绑定新图，再解绑旧图。梗资源和评论图片按其实际持久化行 ID 建立引用；待审核梗修改稿按 revision ID 建立引用，审核通过时把引用迁移到正式梗/资源，驳回、取消或替换时释放审核稿引用。外部链接不进入文件资产表。

## 删除与重试

删除业务数据时，同一数据库事务中删除相应引用；资产仍有其他引用时保持 `ACTIVE`，引用数归零时改为 `PENDING_DELETE`，设置默认 2 天缓冲期并插入 `WAITING` 任务。业务事务不调用 OSS。

Worker 每分钟最多领取一批到期任务，使用数据库租约/行锁防止多实例重复领取。删除前锁定资产并再次确认没有引用，把资产置为 `DELETING`；只允许删除配置的业务前缀，不允许外链、保护前缀或路径穿越。OSS 删除按幂等方式执行，成功后将资产和任务标记完成。失败采用 1 分钟、5 分钟、30 分钟、2 小时、6 小时退避，最多 5 次后进入 `FAILED`，记录可排查的错误摘要及关联信息。已经进入 `DELETING` 的资源不允许重新绑定。

## 临时文件与孤儿对账

- `tmp/` Bucket 生命周期建议在 OSS 控制台配置 2 天过期；应用侧每天清理超过 3 天的 `TEMP` 记录并将仍存在的对象加入可靠删除队列，作为生命周期未配置或上传失败的兜底。数据库行保留为 `DELETED` 状态用于审计，不物理删除。
- 每天凌晨分页列举允许检查的 OSS 前缀，与资产表对账；未知对象登记为 `DISCOVERED`，至少间隔 7 天再次确认。初始 `oss.cleanup.reconcile-dry-run` 默认为 `true`，只记录候选和日志；候选清理需在观察期后显式关闭 dry-run，并再次验证引用、允许删除前缀和保护前缀。
- `system/`、`homepage/static/` 等系统前缀默认保护。OSS 删除只有在允许前缀白名单内才可执行。
- 日志记录 `assetId`、`objectKey`、`bizType`、`bizId`、`reason`、`taskId` 和 `retryCount`；定时输出 TEMP、ACTIVE、PENDING_DELETE、FAILED 及孤儿候选数量，作为首版运行监控。不新增消息队列或去重依赖；SHA-256 去重留作后续优化。

## 接口与兼容

将现有上传凭证接口调整为创建上传票据和确认上传的 REST 接口，并更新用户端和管理端上传工具。已有用户/梗/评论/首页读取响应保持访问 URL；业务写入接口继续使用现有 URL 字段，服务端归一化并解析资产。既有历史完整 URL、外链和非 OSS 本地路径不通过新删除机制处理。

## 验收标准

- 新票据只能为当前用户或已授权管理员生成服务端指定的唯一临时 key；不合法场景、扩展名、MIME、大小及未登录请求被拒绝。
- 未确认、已过期上传不能绑定业务；确认后才能绑定，业务提交回滚时引用与资产状态一起回滚。
- 共享对象在仍有任意业务或审核稿引用时不会进入删除队列；最后一个引用移除后只进入延迟删除。
- 头像、梗封面/资源、评论图片、首页图的创建、替换、删除均维护引用；管理员审核稿通过、拒绝、取消场景不误删文件。
- OSS 删除失败按指定退避重试，达到上限进入 `FAILED`；孤儿扫描默认只 dry-run，并尊重保护/允许前缀。
- Flyway 可在已有数据库上升级，历史 key 仅回填资产引用，不变更现有 URL 字段或已发布业务内容。
- 后端单元/集成与迁移测试通过，用户端和管理端构建通过。

## 明确不包含

不自动创建或修改云端 Bucket 生命周期规则，不新增管理后台资产浏览页面，不引入 MQ，不在首版实现 SHA-256 内容去重，也不清理无法证明归属的历史外链或未知前缀对象。
