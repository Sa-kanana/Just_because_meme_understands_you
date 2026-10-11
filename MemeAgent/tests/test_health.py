import os

os.environ.setdefault("INTERNAL_API_KEY", "test-key")
os.environ.setdefault("VECTOR_DATABASE_URL", "")

from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_without_vector_db():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["service"] == "meme-agent"
    assert "status" in body


def test_rag_log_routes_are_removed():
    route_paths = {route.path for route in app.routes}

    assert "/ops/rag-logs" not in route_paths
    assert "/ops/rag-logs/{event_id}" not in route_paths
