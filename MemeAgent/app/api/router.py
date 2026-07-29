from fastapi import APIRouter

from app.api import crawl, health, ingest, knowledge, stream

api_router = APIRouter()
api_router.include_router(health.router)
api_router.include_router(ingest.router)
api_router.include_router(knowledge.router)
api_router.include_router(stream.router)
api_router.include_router(crawl.router)
