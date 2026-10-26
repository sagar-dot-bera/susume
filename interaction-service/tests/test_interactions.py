from datetime import datetime, timedelta, timezone

import pytest
from httpx import AsyncClient

from tests.conftest import TENANT_A

URL = "/api/v1/interactions"


async def record(client: AsyncClient, headers: dict, user: str, item: str, kind: str = "VIEW"):
    return await client.post(
        URL, headers=headers, json={"externalUserId": user, "externalItemId": item, "interactionType": kind}
    )


# --- auth -------------------------------------------------------------------


@pytest.mark.parametrize("headers", [{}, {"X-API-KEY": "wrong-key"}])
async def test_rejects_missing_or_invalid_api_key(client, headers):
    response = await client.get(f"{URL}/export", headers=headers)

    assert response.status_code == 401
    assert response.json() == {"code": "UNAUTHORIZED", "message": "Invalid API key"}


# --- POST /interactions ------------------------------------------------------


async def test_record_interaction(client, auth_a, cache):
    response = await record(client, auth_a, "user-1", "item-1", "click")

    assert response.status_code == 201
    body = response.json()
    assert body["externalUserId"] == "user-1"
    assert body["externalItemId"] == "item-1"
    assert body["interactionType"] == "CLICK"
    assert body["id"] and body["timestamp"]
    assert cache.evicted == [(TENANT_A, "user-1")]


async def test_record_rejects_unknown_interaction_type(client, auth_a):
    response = await record(client, auth_a, "user-1", "item-1", "teleport")

    assert response.status_code == 422


async def test_record_rejects_unknown_item(client, auth_a, cache):
    response = await record(client, auth_a, "user-1", "no-such-item")

    assert response.status_code == 404
    assert response.json()["code"] == "ITEM_NOT_FOUND"
    assert cache.evicted == []


async def test_record_rejects_inactive_item(client, auth_a):
    response = await record(client, auth_a, "user-1", "item-retired")

    assert response.status_code == 409
    assert response.json()["code"] == "ITEM_INACTIVE"


async def test_record_cannot_use_another_tenants_item(client, auth_a):
    response = await record(client, auth_a, "user-1", "item-b")

    assert response.status_code == 404


# --- GET /interactions/history -----------------------------------------------


async def test_history_is_newest_first_and_paginated(client, auth_a):
    for item in ["item-1", "item-2", "item-1"]:
        await record(client, auth_a, "user-1", item)
    await record(client, auth_a, "someone-else", "item-1")

    first = (await client.get(f"{URL}/history", headers=auth_a, params={"externalUserId": "user-1", "limit": 2})).json()
    second = (
        await client.get(f"{URL}/history", headers=auth_a, params={"externalUserId": "user-1", "limit": 2, "page": 1})
    ).json()

    assert first["totalElements"] == 3
    assert first["totalPages"] == 2
    assert [i["externalItemId"] for i in first["data"]] == ["item-1", "item-2"]
    assert [i["externalItemId"] for i in second["data"]] == ["item-1"]
    timestamps = [i["timestamp"] for i in first["data"] + second["data"]]
    assert timestamps == sorted(timestamps, reverse=True)


async def test_history_filters_by_type(client, auth_a):
    await record(client, auth_a, "user-1", "item-1", "VIEW")
    await record(client, auth_a, "user-1", "item-2", "PURCHASE")

    body = (
        await client.get(
            f"{URL}/history", headers=auth_a, params={"externalUserId": "user-1", "interactionType": "PURCHASE"}
        )
    ).json()

    assert body["totalElements"] == 1
    assert body["data"][0]["externalItemId"] == "item-2"


async def test_history_since_is_applied_before_pagination(client, auth_a):
    await record(client, auth_a, "user-1", "item-1")

    future = (datetime.now(timezone.utc) + timedelta(hours=1)).isoformat()
    past = (datetime.now(timezone.utc) - timedelta(hours=1)).isoformat()

    later = (await client.get(f"{URL}/history", headers=auth_a, params={"externalUserId": "user-1", "since": future})).json()
    earlier = (await client.get(f"{URL}/history", headers=auth_a, params={"externalUserId": "user-1", "since": past})).json()

    assert later["totalElements"] == 0 and later["data"] == []
    assert earlier["totalElements"] == 1


async def test_history_is_scoped_to_the_calling_tenant(client, auth_a, auth_b):
    await record(client, auth_a, "user-1", "item-1")

    body = (await client.get(f"{URL}/history", headers=auth_b, params={"externalUserId": "user-1"})).json()

    assert body["totalElements"] == 0


async def test_history_requires_user_id(client, auth_a):
    response = await client.get(f"{URL}/history", headers=auth_a)

    assert response.status_code == 422


# --- GET /interactions/export ------------------------------------------------


async def test_export_is_oldest_first_and_tenant_scoped(client, auth_a, auth_b):
    await record(client, auth_a, "user-1", "item-1")
    await record(client, auth_a, "user-2", "item-2")
    await record(client, auth_b, "user-9", "item-b")

    body = (await client.get(f"{URL}/export", headers=auth_a, params={"limit": 10})).json()

    assert body["page"] == 0 and body["limit"] == 10
    assert body["totalElements"] == 2 and body["totalPages"] == 1
    assert [i["externalUserId"] for i in body["data"]] == ["user-1", "user-2"]


@pytest.mark.parametrize("params", [{"limit": 0}, {"limit": 1001}, {"page": -1}])
async def test_export_validates_paging(client, auth_a, params):
    response = await client.get(f"{URL}/export", headers=auth_a, params=params)

    assert response.status_code == 422


# --- health ------------------------------------------------------------------


async def test_health(client):
    assert (await client.get("/health")).json() == {"status": "UP", "service": "interaction-service"}
    assert (await client.get("/health/ready")).status_code == 200
