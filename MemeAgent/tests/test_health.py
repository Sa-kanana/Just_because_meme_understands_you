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
