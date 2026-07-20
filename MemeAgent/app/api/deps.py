from typing import Annotated

from fastapi import Depends

from app.core.auth import verify_internal_api_key


InternalAuth = Annotated[None, Depends(verify_internal_api_key)]
