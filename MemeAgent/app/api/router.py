from fastapi import APIRouter

from app.api import health, ingest, stream

api_router = APIRouter()
api_router.include_router(health.router)
api_router.include_router(ingest.router)
api_router.include_router(stream.router)
