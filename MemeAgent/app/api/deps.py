from typing import Annotated

from fastapi import Depends

from app.core.auth import verify_stream_api_key, verify_write_api_key

# 只读：AI 搜索流
StreamAuth = Annotated[None, Depends(verify_stream_api_key)]
# 写：灌库 / 采集（生产建议独立写密钥）
WriteAuth = Annotated[None, Depends(verify_write_api_key)]
# 兼容旧依赖名
InternalAuth = StreamAuth
