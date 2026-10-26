from collections.abc import AsyncIterator

import pytest
from httpx import ASGITransport, AsyncClient

from app.config import Settings
from app.main import create_app
from tests.conftest import TENANT_A, TENANT_B

TOKEN = "internal-test-token"
AUTH = {"X-Internal-Token": TOKEN}


@pytest.fixture
async def internal_client(engine, cache) -> AsyncIterator[AsyncClient]:
    app = create_app(Settings(database_url="sqlite+aiosqlite://", internal_api_token=TOKEN), engine=engine, cache=cache)
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        yield client


async def record(client: AsyncClient, headers: dict, user: str, item: str, kind: str = "VIEW"):
    response = await client.post(
        "/api/v1/interactions",
        headers=headers,
        json={"externalUserId": user, "externalItemId": item, "interactionType": kind},
    )
    assert response.status_code == 201
    return response.json()


async def test_disabled_without_configured_token(client):
    response = await client.get(f"/internal/v1/tenants/{TENANT_A}/events", headers=AUTH)

    assert response.status_code == 503
    assert response.json()["code"] == "INTERNAL_API_DISABLED"


@pytest.mark.parametrize("headers", [{}, {"X-Internal-Token": "wrong"}])
async def test_rejects_bad_token(internal_client, headers):
    response = await internal_client.get(f"/internal/v1/tenants/{TENANT_A}/events", headers=headers)

    assert response.status_code == 401


async def test_tenant_events_are_keyset_paginated_in_time_order(internal_client, auth_a, auth_b):
    for user, item, kind in [("u1", "item-1", "VIEW"), ("u2", "item-2", "CART"), ("u1", "item-2", "PURCHASE")]:
        await record(internal_client, auth_a, user, item, kind)
    await record(internal_client, auth_b, "u9", "item-b")

    url = f"/internal/v1/tenants/{TENANT_A}/events"
    first = (await internal_client.get(url, headers=AUTH, params={"limit": 2})).json()
    second = (await internal_client.get(url, headers=AUTH, params={"limit": 2, "cursor": first["nextCursor"]})).json()

    events = first["data"] + second["data"]
    assert [(e["externalUserId"], e["externalItemId"], e["interactionType"]) for e in events] == [
        ("u1", "item-1", "VIEW"), ("u2", "item-2", "CART"), ("u1", "item-2", "PURCHASE"),
    ]
    assert second["nextCursor"] is None
    timestamps = [e["timestampMs"] for e in events]
    assert timestamps == sorted(timestamps) and all(isinstance(t, int) for t in timestamps)


async def test_rejects_malformed_cursor(internal_client):
    response = await internal_client.get(
        f"/internal/v1/tenants/{TENANT_A}/events", headers=AUTH, params={"cursor": "garbage"}
    )

    assert response.status_code == 400
    assert response.json()["code"] == "INVALID_CURSOR"


async def test_user_events_are_latest_n_oldest_first(internal_client, auth_a):
    for item in ["item-1", "item-2", "item-1"]:
        await record(internal_client, auth_a, "u1", item)
    await record(internal_client, auth_a, "u2", "item-1")

    body = (
        await internal_client.get(f"/internal/v1/tenants/{TENANT_A}/users/u1/events", headers=AUTH, params={"limit": 2})
    ).json()

    assert [e["externalItemId"] for e in body["data"]] == ["item-2", "item-1"]
    assert body["data"][0]["timestampMs"] <= body["data"][1]["timestampMs"]


async def test_user_events_are_tenant_scoped(internal_client, auth_a):
    await record(internal_client, auth_a, "u1", "item-1")

    body = (await internal_client.get(f"/internal/v1/tenants/{TENANT_B}/users/u1/events", headers=AUTH)).json()

    assert body["data"] == []
