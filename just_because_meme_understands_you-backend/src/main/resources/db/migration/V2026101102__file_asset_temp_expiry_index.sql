-- 中文注释：为过期TEMP资产扫描增加匹配索引，避免每日清理任务全表扫描文件资产。
ALTER TABLE file_asset
    ADD INDEX idx_file_asset_temp_created (status, created_at, id)
    COMMENT '按资产状态和创建时间扫描过期TEMP对象';
