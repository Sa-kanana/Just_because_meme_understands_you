import os
from types import SimpleNamespace

os.environ.setdefault("INTERNAL_API_KEY", "test-key")
os.environ.setdefault("VECTOR_DATABASE_URL", "")

from fastapi.testclient import TestClient

from app.api import ingest as ingest_api
from app.core import auth
from app.main import app


def test_meme_id_page_requires_write_auth_and_returns_ids_only(monkeypatch):
    monkeypatch.setattr(
        auth,
        "get_settings",
        lambda: SimpleNamespace(
            internal_api_key="stream-key",
            internal_api_key_write="write-key",
        ),
    )
    monkeypatch.setattr(
        ingest_api,
        "list_meme_ids_page",
        lambda cursor, limit: _page_result(cursor, limit),
        raising=False,
    )

    route_registered = any(
        route.path == "/ingest/meme-ids" and "GET" in route.methods
        for route in app.routes
    )
    assert route_registered

    client = TestClient(app)
    unauthorized = client.get("/ingest/meme-ids")
    invalid_key = client.get(
        "/ingest/meme-ids", headers={"X-Internal-Api-Key": "stream-key"}
    )
    response = client.get(
        "/ingest/meme-ids?cursor=101&limit=2",
        headers={"X-Internal-Api-Key": "write-key"},
    )

    assert unauthorized.status_code == 401
    assert invalid_key.status_code == 401
    assert response.status_code == 200
    assert response.json() == {"meme_ids": ["102", "103"], "next_cursor": None}
    assert set(response.json()) == {"meme_ids", "next_cursor"}


async def _page_result(cursor, limit):
    assert cursor == "101"
    assert limit == 2
    return ["102", "103"], None
