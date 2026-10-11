-- 中文说明：梗详情页资料卡没有应用内数据写入来源，已移除对应界面与接口；Agent/管理端知识库使用独立的 PostgreSQL 向量表，不依赖此表。
DROP TABLE IF EXISTS meme_knowledge_card;
