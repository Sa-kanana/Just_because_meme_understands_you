from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.router import api_router
from app.core.lifespan import lifespan
from app.core.settings import get_settings
import logging

logger = logging.getLogger(__name__)


def create_app() -> FastAPI:
    settings = get_settings()
    # 不打印完整 base_url / key，避免日志泄露
    logger.info(
        "MemeAgent boot env=%s chat_model=%s write_key_separated=%s",
        settings.app_env,
        settings.openai_chat_model,
        bool((settings.internal_api_key_write or "").strip()),
    )
    app = FastAPI(
        title=settings.app_name,
        version="0.1.0",
        lifespan=lifespan,
        docs_url="/docs" if settings.is_dev else None,
        redoc_url=None,
    )
    app.include_router(api_router)

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(request: Request, exc: RequestValidationError):
        logger.warning(
            "Request validation failed path=%s detail=%s",
            request.url.path,
            exc.errors(),
        )
        return JSONResponse(status_code=422, content={"detail": exc.errors()})

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn

    settings = get_settings()
    uvicorn.run(
        "app.main:app",
        host=settings.app_host,
        port=settings.app_port,
        reload=settings.is_dev,
    )
