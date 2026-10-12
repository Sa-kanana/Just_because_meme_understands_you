"""MemeAgent 入库与分页共享的业务上限。"""

# 中文注释：单个梗最多保存的向量片段数，同时供入库和检索候选估算使用。
MAX_CHUNKS_PER_MEME = 6
# 中文注释：单个向量片段正文、标题和标签前缀合计允许的最大字符数。
MAX_CHUNK_CHARS = 1200
# 中文注释：单个梗允许关联的标签数量上限。
MAX_TAG_COUNT = 20
# 中文注释：单个标签去除首尾空白后的最大字符数。
MAX_TAG_CHARS = 64
# 中文注释：所有标签去除首尾空白后的累计字符数上限。
MAX_TOTAL_TAG_CHARS = 240
# 中文注释：管理端向量 ID 游标分页接口单页允许返回的最大梗 ID 数。
MAX_MEME_ID_PAGE_SIZE = 500
