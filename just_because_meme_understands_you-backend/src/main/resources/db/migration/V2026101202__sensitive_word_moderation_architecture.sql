-- 中文注释：从旧 action_type 转换为分类与风险等级分离的结构，并增加多实例词库版本记录。
-- 中文注释：旧 action_type=1 的来源值映射为 risk_level=NORMAL，并将旧固定替换行为迁移到 replacement='***'。
-- 中文注释：旧 action_type=2 的来源值映射为 risk_level=REVIEW；旧 action_type=3 的来源值映射为 risk_level=BLOCK。
SET @has_risk_level = (SELECT COUNT(*) FROM information_schema.columns
                       WHERE table_schema = DATABASE() AND table_name = 'sensitive_word'
                         AND column_name = 'risk_level');
SET @add_risk_level = IF(@has_risk_level = 0,
    'ALTER TABLE sensitive_word ADD COLUMN risk_level VARCHAR(16) NOT NULL DEFAULT ''NORMAL'' COMMENT ''风险等级（NORMAL:允许或替换, REVIEW:人工审核, BLOCK:直接拒绝）''',
    'SELECT 1');
PREPARE add_risk_level_stmt FROM @add_risk_level;
EXECUTE add_risk_level_stmt;
DEALLOCATE PREPARE add_risk_level_stmt;

-- 中文注释：match_type 固定保存规则匹配方式，NORMAL 表示标准化后按字面匹配。
SET @has_match_type = (SELECT COUNT(*) FROM information_schema.columns
                       WHERE table_schema = DATABASE() AND table_name = 'sensitive_word'
                         AND column_name = 'match_type');
SET @add_match_type = IF(@has_match_type = 0,
    'ALTER TABLE sensitive_word ADD COLUMN match_type VARCHAR(20) NOT NULL DEFAULT ''NORMAL'' COMMENT ''匹配方式（NORMAL:标准化后按字面匹配）''',
    'SELECT 1');
PREPARE add_match_type_stmt FROM @add_match_type;
EXECUTE add_match_type_stmt;
DEALLOCATE PREPARE add_match_type_stmt;

-- 中文注释：replacement 保存旧替换策略的替换文本；非 NORMAL 规则的替换值保持为空。
SET @has_replacement = (SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = 'sensitive_word'
                          AND column_name = 'replacement');
SET @add_replacement = IF(@has_replacement = 0,
    'ALTER TABLE sensitive_word ADD COLUMN replacement VARCHAR(20) NULL COMMENT ''NORMAL 等级命中后的替换文本，空值表示保留原文''',
    'SELECT 1');
PREPARE add_replacement_stmt FROM @add_replacement;
EXECUTE add_replacement_stmt;
DEALLOCATE PREPARE add_replacement_stmt;

-- 中文注释：version 保存单个敏感词记录的修订版本，用于审计词条自身的变更序号。
SET @has_word_version = (SELECT COUNT(*) FROM information_schema.columns
                         WHERE table_schema = DATABASE() AND table_name = 'sensitive_word'
                           AND column_name = 'version');
SET @add_word_version = IF(@has_word_version = 0,
    'ALTER TABLE sensitive_word ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT ''该敏感词记录的修订版本''',
    'SELECT 1');
PREPARE add_word_version_stmt FROM @add_word_version;
EXECUTE add_word_version_stmt;
DEALLOCATE PREPARE add_word_version_stmt;

-- 中文注释：按旧 action_type 的每个值逐项复制到新风险等级和替换文本，确保已有规则行为不变。
SET @has_action_type = (SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = 'sensitive_word'
                          AND column_name = 'action_type');
SET @copy_old_policy = IF(@has_action_type = 1,
    'UPDATE sensitive_word SET risk_level = CASE action_type WHEN 1 THEN ''NORMAL'' WHEN 2 THEN ''REVIEW'' WHEN 3 THEN ''BLOCK'' ELSE ''BLOCK'' END, replacement = CASE action_type WHEN 1 THEN ''***'' ELSE NULL END',
    'SELECT 1');
PREPARE copy_old_policy_stmt FROM @copy_old_policy;
EXECUTE copy_old_policy_stmt;
DEALLOCATE PREPARE copy_old_policy_stmt;

-- 中文注释：转换完成后删除旧 action_type 字段，避免管理端继续写入两套互相矛盾的策略。
SET @drop_action_type = IF(@has_action_type = 1,
    'ALTER TABLE sensitive_word DROP COLUMN action_type',
    'SELECT 1');
PREPARE drop_action_type_stmt FROM @drop_action_type;
EXECUTE drop_action_type_stmt;
DEALLOCATE PREPARE drop_action_type_stmt;

-- 中文注释：创建敏感词全局版本单例表，为 Pub/Sub 通知和定时对账提供版本来源。
CREATE TABLE IF NOT EXISTS sensitive_word_dictionary_meta
(
    id         TINYINT  NOT NULL COMMENT '单例元数据行主键，固定为1' PRIMARY KEY,
    version    BIGINT   NOT NULL DEFAULT 0 COMMENT '全局词库版本，每次管理变更递增',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '全局词库最近更新时间'
) COMMENT '敏感词词库全局版本表' COLLATE = utf8mb4_general_ci;

-- 中文注释：初始化单例版本行；若之前已初始化则保留现有版本，不回退版本号。
INSERT INTO sensitive_word_dictionary_meta (id, version)
VALUES (1, 0)
ON DUPLICATE KEY UPDATE version = version;
