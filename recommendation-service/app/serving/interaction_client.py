from dataclasses import dataclass
from typing import Iterator, List, Optional
from urllib.parse import quote

import httpx


@dataclass(frozen=True)
class RawEvent:
    user_id: str
    item_id: str
    interaction_type: str
    timestamp_ms: int


def _parse(rows: list) -> List[RawEvent]:
    return [RawEvent(r["externalUserId"], r["externalItemId"], r["interactionType"], int(r["timestampMs"])) for r in rows]


class InteractionClient:
    """Reads raw events from interaction-service's internal API."""

    def __init__(self, base_url: str, token: str, timeout_s: float = 5.0, transport: Optional[httpx.BaseTransport] = None):
        self._http = httpx.Client(
            base_url=base_url.rstrip("/"),
            headers={"X-Internal-Token": token},
            timeout=timeout_s,
            transport=transport,
        )

    def tenant_events(self, tenant_id: str, page_size: int = 10000) -> Iterator[RawEvent]:
        """Every event of the tenant, oldest first."""
        cursor = None
        while True:
            params = {"limit": page_size}
            if cursor:
                params["cursor"] = cursor
            response = self._http.get(f"/internal/v1/tenants/{tenant_id}/events", params=params)
            response.raise_for_status()
            body = response.json()
            yield from _parse(body["data"])
            cursor = body.get("nextCursor")
            if not cursor:
                return

    def user_events(self, tenant_id: str, user_id: str, limit: int = 5000) -> List[RawEvent]:
        """The user's most recent events, oldest first."""
        response = self._http.get(f"/internal/v1/tenants/{tenant_id}/users/{quote(user_id, safe='')}/events", params={"limit": limit})
        response.raise_for_status()
        return _parse(response.json()["data"])

    def close(self) -> None:
        self._http.close()
