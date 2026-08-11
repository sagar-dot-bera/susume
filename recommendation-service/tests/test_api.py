import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)


def test_health_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP", "service": "recommendation-service"}


def test_rank_endpoint_success():
    payload = {
        "user": {
            "id": "user-123",
            "features": {
                "totalViews": 42,
                "totalClicks": 18,
                "totalLikes": 7,
                "totalPurchases": 2
            }
        },
        "context": {
            "surface": "homepage",
            "hour": 18,
            "dayOfWeek": 2
        },
        "candidates": [
            {
                "itemId": "item-101",
                "strategyScores": {
                    "contentBased": 0.91,
                    "collaborativeFiltering": 0.73,
                    "trending": 0.64
                },
                "itemFeatures": {
                    "itemPopularity": 0.72,
                    "itemAge": 12.0
                },
                "userItemFeatures": {
                    "previousViews": 2,
                    "previousClicks": 1
                }
            },
            {
                "itemId": "item-205",
                "strategyScores": {
                    "contentBased": 0.35,
                    "collaborativeFiltering": 0.95,
                    "trending": 0.81
                }
            }
        ],
        "limit": 10
    }

    response = client.post("/api/v1/recommendations/rank", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert "recommendations" in data
    assert "model" in data
    assert len(data["recommendations"]) == 2
    assert "itemId" in data["recommendations"][0]
    assert "score" in data["recommendations"][0]
    assert data["model"]["name"] == "susume-ranker"


def test_rank_endpoint_empty_candidates():
    payload = {
        "user": {"id": "user-1"},
        "candidates": [],
        "limit": 5
    }
    response = client.post("/api/v1/recommendations/rank", json=payload)
    assert response.status_code == 200
    assert response.json()["recommendations"] == []
