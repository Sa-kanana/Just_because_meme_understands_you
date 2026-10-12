-- 中文注释：为标签增加分类和展示顺序，关联表增加主标签标记，并补齐首批标准标签。
-- 中文注释：根据当前数据库的字段元数据检查 category 是否存在；不存在时添加，已存在时执行无副作用语句以支持部分迁移后重试。
SET @tag_category_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'meme_tag' AND column_name = 'category') = 0,
    'ALTER TABLE meme_tag ADD COLUMN category VARCHAR(24) NOT NULL DEFAULT ''UNCATEGORIZED'' COMMENT ''标签所属分类代码'' AFTER name',
    'SELECT 1'
);
PREPARE tag_category_stmt FROM @tag_category_ddl;
EXECUTE tag_category_stmt;
DEALLOCATE PREPARE tag_category_stmt;

-- 中文注释：根据当前数据库的字段元数据检查 sort_order 是否存在；不存在时添加，已存在时跳过。
SET @tag_sort_order_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'meme_tag' AND column_name = 'sort_order') = 0,
    'ALTER TABLE meme_tag ADD COLUMN sort_order INT NOT NULL DEFAULT 0 COMMENT ''分类内展示顺序，数值越小越靠前'' AFTER category',
    'SELECT 1'
);
PREPARE tag_sort_order_stmt FROM @tag_sort_order_ddl;
EXECUTE tag_sort_order_stmt;
DEALLOCATE PREPARE tag_sort_order_stmt;

-- 中文注释：仅在分类排序索引不存在时创建，避免部分迁移重试产生重复索引。
SET @tag_category_index_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'meme_tag' AND index_name = 'idx_meme_tag_category_order') = 0,
    'CREATE INDEX idx_meme_tag_category_order ON meme_tag (category, sort_order, id)',
    'SELECT 1'
);
PREPARE tag_category_index_stmt FROM @tag_category_index_ddl;
EXECUTE tag_category_index_stmt;
DEALLOCATE PREPARE tag_category_index_stmt;

-- 中文注释：根据当前数据库的字段元数据检查 is_primary 是否存在；不存在时添加并默认将历史关联标记为普通标签。
SET @tag_is_primary_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'meme_tag_relation' AND column_name = 'is_primary') = 0,
    'ALTER TABLE meme_tag_relation ADD COLUMN is_primary TINYINT NOT NULL DEFAULT 0 COMMENT ''是否为该梗的主分类标签，1是0否'' AFTER meme_tag_id',
    'SELECT 1'
);
PREPARE tag_is_primary_stmt FROM @tag_is_primary_ddl;
EXECUTE tag_is_primary_stmt;
DEALLOCATE PREPARE tag_is_primary_stmt;

-- 中文注释：来源为每条梗现存的标签关联，目标为该梗最小 meme_tag_id 对应关联的 is_primary=1。
UPDATE meme_tag_relation AS relation_row
JOIN (
    SELECT meme_id, MIN(meme_tag_id) AS primary_tag_id
    FROM meme_tag_relation
    GROUP BY meme_id
) AS legacy_primary ON legacy_primary.meme_id = relation_row.meme_id
                    AND legacy_primary.primary_tag_id = relation_row.meme_tag_id
SET relation_row.is_primary = 1;

-- 中文注释：仅在辅助列不存在时添加；应用写入主标签行的 meme_id，普通标签保持 NULL。
SET @tag_primary_id_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'meme_tag_relation' AND column_name = 'primary_meme_id') = 0,
    'ALTER TABLE meme_tag_relation ADD COLUMN primary_meme_id BIGINT NULL COMMENT ''主标签唯一约束辅助列，由应用在主标签关联中写入梗ID''',
    'SELECT 1'
);
PREPARE tag_primary_id_stmt FROM @tag_primary_id_ddl;
EXECUTE tag_primary_id_stmt;
DEALLOCATE PREPARE tag_primary_id_stmt;

-- 中文注释：来源为已回填的 meme_id、is_primary，目标为主标签关联的 primary_meme_id；普通标签目标值为 NULL。
UPDATE meme_tag_relation
SET primary_meme_id = CASE WHEN is_primary = 1 THEN meme_id ELSE NULL END;

-- 中文注释：仅在唯一索引不存在时创建；每条梗最多一条 primary_meme_id 非空的关联记录。
SET @tag_primary_index_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE() AND table_name = 'meme_tag_relation' AND index_name = 'uk_meme_tag_primary_per_meme') = 0,
    'CREATE UNIQUE INDEX uk_meme_tag_primary_per_meme ON meme_tag_relation (primary_meme_id)',
    'SELECT 1'
);
PREPARE tag_primary_index_stmt FROM @tag_primary_index_ddl;
EXECUTE tag_primary_index_stmt;
DEALLOCATE PREPARE tag_primary_index_stmt;

-- 中文注释：临时种子表只承载标准标签名称、分类代码和分类内排序，已有标签 ID 与关联不变。
CREATE TEMPORARY TABLE tmp_standard_meme_tag
(
    name VARCHAR(50) NOT NULL COMMENT '标准标签名称，匹配已有标签时复用原记录',
    category VARCHAR(24) NOT NULL COMMENT '标准标签所属分类代码',
    sort_order INT NOT NULL COMMENT '标签在所属分类中的展示顺序'
) COMMENT '首批标准标签迁移临时表';

-- 中文注释：首批标准标签共 80 个；相同名称按名称复用，避免为已有标签重复创建记录。
INSERT INTO tmp_standard_meme_tag (name, category, sort_order)
VALUES
    ('B站', 'SOURCE', 1),
    ('抖音', 'SOURCE', 2),
    ('微博', 'SOURCE', 3),
    ('贴吧', 'SOURCE', 4),
    ('知乎', 'SOURCE', 5),
    ('小红书', 'SOURCE', 6),
    ('游戏', 'SOURCE', 7),
    ('动漫', 'SOURCE', 8),
    ('影视', 'SOURCE', 9),
    ('谐音', 'FORMAT', 1),
    ('空耳', 'FORMAT', 2),
    ('缩写', 'FORMAT', 3),
    ('表情包', 'FORMAT', 4),
    ('名场面', 'FORMAT', 5),
    ('句式梗', 'FORMAT', 6),
    ('热词', 'FORMAT', 7),
    ('弹幕', 'FORMAT', 8),
    ('鬼畜', 'FORMAT', 9),
    ('二创', 'FORMAT', 10),
    ('口头禅', 'FORMAT', 11),
    ('梗图', 'FORMAT', 12),
    ('搞笑', 'EMOTION', 1),
    ('抽象', 'EMOTION', 2),
    ('无语', 'EMOTION', 3),
    ('震惊', 'EMOTION', 4),
    ('破防', 'EMOTION', 5),
    ('治愈', 'EMOTION', 6),
    ('怀旧', 'EMOTION', 7),
    ('阴阳', 'EMOTION', 8),
    ('尴尬', 'EMOTION', 9),
    ('悲伤', 'EMOTION', 10),
    ('热血', 'EMOTION', 11),
    ('可爱', 'EMOTION', 12),
    ('自嘲', 'EMOTION', 13),
    ('聊天', 'SCENARIO', 1),
    ('评论区', 'SCENARIO', 2),
    ('校园', 'SCENARIO', 3),
    ('职场', 'SCENARIO', 4),
    ('恋爱', 'SCENARIO', 5),
    ('友情', 'SCENARIO', 6),
    ('追星', 'SCENARIO', 7),
    ('消费', 'SCENARIO', 8),
    ('旅游', 'SCENARIO', 9),
    ('考试', 'SCENARIO', 10),
    ('加班', 'SCENARIO', 11),
    ('社交', 'SCENARIO', 12),
    ('人物', 'CONTENT', 1),
    ('明星', 'CONTENT', 2),
    ('动物', 'CONTENT', 3),
    ('猫', 'CONTENT', 4),
    ('二次元', 'CONTENT', 5),
    ('游戏角色', 'CONTENT', 6),
    ('生活', 'CONTENT', 7),
    ('社会热点', 'CONTENT', 8),
    ('经典老梗', 'ERA', 1),
    ('00年代', 'ERA', 2),
    ('2010-2015', 'ERA', 3),
    ('2016-2020', 'ERA', 4),
    ('2021-2023', 'ERA', 5),
    ('2024', 'ERA', 6),
    ('2025', 'ERA', 7),
    ('2026', 'ERA', 8),
    ('全网爆火', 'PROPAGATION', 1),
    ('B站热门', 'PROPAGATION', 2),
    ('短视频热门', 'PROPAGATION', 3),
    ('年度热词', 'PROPAGATION', 4),
    ('弹幕梗', 'PROPAGATION', 5),
    ('地域梗', 'PROPAGATION', 6),
    ('方言梗', 'PROPAGATION', 7),
    ('圈内梗', 'PROPAGATION', 8),
    ('过气梗', 'PROPAGATION', 9),
    ('夸赞', 'TONE', 1),
    ('吐槽', 'TONE', 2),
    ('反讽', 'TONE', 3),
    ('调侃', 'TONE', 4),
    ('鼓励', 'TONE', 5),
    ('拒绝', 'TONE', 6),
    ('惊讶', 'TONE', 7),
    ('抱怨', 'TONE', 8),
    ('嘲讽', 'TONE', 9);

-- 中文注释：来源为标准清单和 meme_tag.name，目标为已有同名标签的 category、sort_order；关联 ID、数量不变。
UPDATE meme_tag AS existing_tag
JOIN tmp_standard_meme_tag AS standard_tag ON standard_tag.name = existing_tag.name
SET existing_tag.category = standard_tag.category,
    existing_tag.sort_order = standard_tag.sort_order;

-- 中文注释：来源为标准清单中尚不存在的 name、category、sort_order，目标为新增 meme_tag 行；初始关联数量为 0。
INSERT INTO meme_tag (name, category, sort_order, related_quantity)
SELECT standard_tag.name, standard_tag.category, standard_tag.sort_order, 0
FROM tmp_standard_meme_tag AS standard_tag
WHERE NOT EXISTS (
    SELECT 1 FROM meme_tag AS existing_tag WHERE existing_tag.name = standard_tag.name
);

DROP TEMPORARY TABLE tmp_standard_meme_tag;
