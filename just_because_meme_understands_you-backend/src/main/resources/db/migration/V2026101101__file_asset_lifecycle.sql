-- 中文注释：新增文件资产生命周期表；本迁移不复制或改写现有业务表字段。
CREATE TABLE IF NOT EXISTS file_asset (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件资产主键',
    object_key VARCHAR(512) NOT NULL COMMENT 'OSS对象键，不包含访问域名',
    owner_id BIGINT NULL COMMENT '上传者用户ID，历史回填资产可为空',
    scene VARCHAR(32) NOT NULL COMMENT '上传业务场景，例如avatar、meme或comment',
    original_filename VARCHAR(255) NULL COMMENT '客户端声明的原始文件名，仅用于展示和审计',
    content_type VARCHAR(128) NULL COMMENT '文件MIME类型，历史资产未知时为空',
    file_size BIGINT UNSIGNED NULL COMMENT '文件字节数，历史资产未知时为空',
    status VARCHAR(24) NOT NULL DEFAULT 'TEMP' COMMENT '资产状态：TEMP、ACTIVE、PENDING_DELETE、DELETING或DELETED',
    uploaded_at DATETIME NULL COMMENT '服务端确认OSS上传成功的时间',
    activated_at DATETIME NULL COMMENT '资产首次绑定业务引用的时间',
    delete_after DATETIME NULL COMMENT '允许执行OSS删除的最早时间',
    deleted_at DATETIME NULL COMMENT 'OSS对象确认删除的时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '资产记录创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '资产记录最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_asset_object_key (object_key),
    KEY idx_file_asset_status (status, delete_after, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS文件资产及其生命周期状态';

CREATE TABLE IF NOT EXISTS file_asset_ref (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件资产引用主键',
    asset_id BIGINT NOT NULL COMMENT '被业务引用的文件资产ID',
    biz_type VARCHAR(32) NOT NULL COMMENT '引用方业务类型，例如USER、MEME或BANNER',
    biz_id BIGINT NOT NULL COMMENT '引用方业务记录ID',
    field_key VARCHAR(128) NOT NULL COMMENT '业务字段位置，例如AVATAR、COVER或资源行ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '业务引用创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_asset_ref_business_field (biz_type, biz_id, field_key),
    KEY idx_file_asset_ref_asset (asset_id),
    CONSTRAINT fk_file_asset_ref_asset FOREIGN KEY (asset_id) REFERENCES file_asset (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件资产到业务字段的多态引用';

CREATE TABLE IF NOT EXISTS file_delete_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件删除任务主键',
    asset_id BIGINT NOT NULL COMMENT '待删除的文件资产ID',
    object_key VARCHAR(512) NOT NULL COMMENT '创建任务时的OSS对象键快照',
    reason VARCHAR(64) NOT NULL COMMENT '触发删除任务的业务原因',
    status VARCHAR(16) NOT NULL DEFAULT 'WAITING' COMMENT '任务状态：WAITING、PROCESSING、DONE、CANCELED或FAILED',
    retry_count TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已执行的删除尝试次数',
    next_attempt_at DATETIME NOT NULL COMMENT '下一次允许执行任务的时间',
    lease_owner VARCHAR(128) NULL COMMENT '当前领取任务的应用实例标识',
    lease_until DATETIME NULL COMMENT '当前任务租约过期时间',
    started_at DATETIME NULL COMMENT '最近一次开始处理的时间',
    finished_at DATETIME NULL COMMENT '任务完成、取消或终止的时间',
    last_error VARCHAR(1000) NULL COMMENT '最近一次删除失败的错误摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '任务创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '任务最后更新时间',
    PRIMARY KEY (id),
    KEY idx_file_delete_due (status, next_attempt_at, id),
    KEY idx_file_delete_lease (status, lease_until, id),
    KEY idx_file_delete_asset (asset_id),
    CONSTRAINT fk_file_delete_task_asset FOREIGN KEY (asset_id) REFERENCES file_asset (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务内创建并由定时Worker处理的OSS删除任务';

CREATE TABLE IF NOT EXISTS file_orphan_candidate (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'OSS孤儿候选主键',
    object_key VARCHAR(512) NOT NULL COMMENT 'OSS中发现但资产表未登记的对象键',
    status VARCHAR(16) NOT NULL DEFAULT 'DISCOVERED' COMMENT '候选状态：DISCOVERED、REFERENCED、QUEUED或SKIPPED',
    first_seen_at DATETIME NOT NULL COMMENT '首次发现该对象的时间',
    last_seen_at DATETIME NOT NULL COMMENT '最近一次仍发现该对象的时间',
    confirmed_at DATETIME NULL COMMENT '满足最短复查间隔后的确认时间',
    last_check_result VARCHAR(500) NULL COMMENT '最近一次对账检查结果摘要',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '候选记录创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '候选记录最后更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_orphan_object_key (object_key),
    KEY idx_file_orphan_review (status, first_seen_at, last_seen_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OSS与数据库对账发现的孤儿对象候选';

CREATE TABLE IF NOT EXISTS file_asset_backfill_state (
    backfill_key VARCHAR(64) NOT NULL COMMENT '本次历史资产回填任务的唯一名称',
    source_name VARCHAR(128) NOT NULL COMMENT '当前扫描的数据源及业务字段名称',
    last_source_id BIGINT NULL COMMENT '当前数据源最近完成回填的主键游标',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '回填状态：PENDING、RUNNING、COMPLETED或FAILED',
    lease_owner VARCHAR(128) NULL COMMENT '当前持有回填租约的应用实例标识',
    lease_until DATETIME NULL COMMENT '回填租约过期时间',
    started_at DATETIME NULL COMMENT '本次回填开始时间',
    completed_at DATETIME NULL COMMENT '全部数据源回填完成时间',
    last_error VARCHAR(1000) NULL COMMENT '最近一次回填失败的错误摘要',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '回填状态最后更新时间',
    PRIMARY KEY (backfill_key, source_name),
    KEY idx_file_backfill_status (status, lease_until, backfill_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='历史文件资产回填检查点和多实例执行租约';
