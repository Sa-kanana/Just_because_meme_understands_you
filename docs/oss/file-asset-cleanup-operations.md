# OSS 文件资产清理运维说明

文件资产以 `file_asset` 登记对象键，以 `file_asset_ref` 登记业务字段引用。业务释放最后一个引用时只创建 `file_delete_task`；专用 Worker 在等待期后重新锁定资产、检查引用和回填门禁，才调用 OSS 删除。`file_orphan_candidate` 用于发现未登记对象，默认只记录，不创建删除任务。

## 上线顺序

1. 先运行 Flyway 迁移并确认 `file_asset`、`file_asset_ref`、`file_delete_task`、`file_orphan_candidate`、`file_asset_backfill_state` 已创建。历史回填运行完毕前，Worker 不会物理删除对象。
2. 在阿里云 OSS 控制台，为当前业务 Bucket 配置仅匹配 `tmp/` 的生命周期规则：创建后 **2 天过期**。只选 `tmp/`；不得把 `avatar/`、`memes/`、`comments/`、`home/` 或 `common/` 纳入该规则。Bucket 生命周期由运维在控制台设置，本项目不自动修改。
3. 先保持 `OSS_RECONCILE_DRY_RUN=true`，设 `OSS_RECONCILE_ENABLED=true`，观察至少 **3–7 天**。如果计划让孤儿对象自动入队，还要保证首次发现后满 7 天且对象最近修改时间也早于 7 天；实际入队前还会检查历史回填完成状态。
4. 核对候选、业务数据和日志，确认没有未回填或共享的对象。审核通过后再明确设置 `OSS_RECONCILE_DRY_RUN=false`。回滚时把它设回 `true`；已经创建的任务需要单独审核，改回 dry-run 不会撤销现有任务。

## 配置项

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `OSS_CLEANUP_ENABLED` | `true` | 启用已入队任务的 Worker 和临时资产巡检。 |
| `OSS_CLEANUP_BATCH_SIZE` | `100` | 每轮最多处理的资产或删除任务数量。 |
| `OSS_CLEANUP_POLL_INTERVAL_MS` | `60000` | Worker 的轮询间隔，单位毫秒。 |
| `OSS_CLEANUP_DELETION_DELAY_DAYS` | `2` | 引用释放或孤儿入队后至少等待的天数。 |
| `OSS_CLEANUP_MAX_RETRIES` | `5` | 删除请求失败时的最多重试次数。 |
| `OSS_CLEANUP_LEASE_SECONDS` | `300` | 任务领取租约有效时间，单位秒。 |
| `OSS_CLEANUP_WORKER_ID` | 启动时生成 | 多实例之间区分领取者的唯一标识。 |
| `OSS_BACKFILL_ENABLED` | `true` | 是否运行历史业务引用回填。 |
| `OSS_BACKFILL_BATCH_SIZE` | `200` | 每个历史来源单轮读取的记录数。 |
| `OSS_BACKFILL_POLL_INTERVAL_MS` | `1000` | 历史回填轮询间隔，单位毫秒。 |
| `OSS_BACKFILL_LEASE_SECONDS` | `300` | 历史回填全局租约有效时间，单位秒。 |
| `OSS_TEMP_CLEANUP_CRON` | 每天 03:00 | 过期临时资产巡检时间。 |
| `OSS_TEMP_ASSET_TTL_DAYS` | `3` | 未确认临时资产进入巡检的最短天数。OSS 生命周期可能先删掉对象，巡检会把相应记录标为已删除。 |
| `OSS_RECONCILE_ENABLED` | `false` | 是否运行OSS对象列举和孤儿候选对账。 |
| `OSS_RECONCILE_CRON` | 每天 03:30 | 孤儿对象对账时间。 |
| `OSS_RECONCILE_PAGE_SIZE` | `500` | 每页列举对象数，上限 1000。 |
| `OSS_ORPHAN_REVIEW_DAYS` | `7` | 候选首次发现和对象最近修改时间均需早于该观察期；程序最少强制 7 天。 |
| `OSS_RECONCILE_DRY_RUN` | `true` | 为真时仅确认候选，不创建删除任务。 |
| `oss.cleanup.reconcile-prefixes` | `tmp/`、`avatar/`、`memes/`、`comments/`、`home/`、`common/` | 允许列举的业务前缀；需同时落在删除白名单内。 |
| `oss.cleanup.allowed-prefixes` | 同上 | Worker 和对账共同使用的可删除对象前缀白名单。 |
| `oss.cleanup.protected-prefixes` | `system/`、`homepage/static/` | 即使误加入白名单也禁止对账入队和物理删除的前缀。 |

## 每日检查

对账结束日志包含本轮 `scanned`、`discovered`、`confirmed`、`queued`、`skipped` 和 `assetsTemp`、`assetsActive`、`assetsPending`、`candidatesDiscovered`、`candidatesQueued`。单对象日志按可用值记录 `assetId`、`objectKey`、`bizType`、`bizId`、`reason`、`taskId`、`retryCount`。首次上线应核对扫描量是否接近预期；`skipped` 或 `queued` 突增时先把 dry-run 设回 `true` 并调查来源。

数据库检查时逐项查看：

- `file_asset.status`：`TEMP` 是尚未确认或待巡检的临时对象，`ACTIVE` 有业务使用，`PENDING_DELETE` 等待删除，`DELETING` 正由 Worker 处理，`DELETED` 已确认删除。
- `file_orphan_candidate.status`：`DISCOVERED` 是待观察或 dry-run 已确认的对象，`REFERENCED` 表示它后来已有资产登记，`QUEUED` 表示已创建延迟删除任务。
- `file_delete_task.status`：`WAITING`、`PROCESSING`、`DONE`、`CANCELED`、`FAILED`；查看 `last_error` 和 `retry_count` 排查失败任务。
- `file_asset_backfill_state`：每个来源以及 `__runner__` 都须为 `COMPLETED`，否则物理删除门禁保持关闭。

确认误判时，先关闭对账入队并保留日志；不要直接删除数据库记录。若任务仍未开始，核对并恢复业务引用可使 Worker 在删除前取消任务。若任务已经 `DONE`，需从备份或源文件恢复对象。OSS 生命周期 `tmp/` 的过期删除不经过应用 Worker，因此临时文件不能作为长期业务引用。
