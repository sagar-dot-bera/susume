from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP", "service": "recommendation-service"}


def test_rank_returns_503_without_feature_store_so_backend_falls_back():
    # The test environment has no INTERNAL_API_TOKEN, so features can't be computed.
    payload = {"tenantId": "t", "user": {"id": "user-1"}, "candidates": [{"itemId": "item-1"}], "limit": 5}

    response = client.post("/api/v1/recommendations/rank", json=payload)

    assert response.status_code == 503
