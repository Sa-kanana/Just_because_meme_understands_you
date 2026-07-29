-- 梗生命周期扩展（主动下架 / 风控锁定 / 申诉）
-- status: 1正常 2审核中 3主动下架 4永久封禁 5风控锁定 6恢复审核中

ALTER TABLE meme
  ADD COLUMN IF NOT EXISTS appeal_reject_count INT NOT NULL DEFAULT 0 COMMENT '申诉驳回次数' AFTER offline_from_status,
  ADD COLUMN IF NOT EXISTS offline_reason VARCHAR(512) NULL COMMENT '下架/锁定原因' AFTER appeal_reject_count;
