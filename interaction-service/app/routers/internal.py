"""
Service-to-service endpoints used by the ML recommendation-service to build ranking
features from raw events. Not for tenants: authenticated with a shared token, not an API key.
"""
import base64
import secrets
import uuid
from datetime import datetime, timezone
from typing import Annotated

from fastapi import APIRouter, Depends, Header, Path, Query, Request

from app.dependencies import RepositoryDep
from app.errors import AppError, UnauthorizedError
from app.models import Interaction
from app.schemas import CamelModel, ErrorResponse


class InternalApiDisabledError(AppError):
    status_code = 503
    code = "INTERNAL_API_DISABLED"


class InvalidCursorError(AppError):
    code = "INVALID_CURSOR"


def require_internal_token(
    request: Request, x_internal_token: Annotated[str | None, Header(alias="X-Internal-Token")] = None
) -> None:
    expected = request.app.state.settings.internal_api_token
    if not expected:
        raise InternalApiDisabledError("Internal API is not configured")
    if not x_internal_token or not secrets.compare_digest(x_internal_token, expected):
        raise UnauthorizedError("Invalid internal token")


router = APIRouter(
    prefix="/internal/v1/tenants/{tenant_id}",
    tags=["Internal"],
    dependencies=[Depends(require_internal_token)],
    responses={401: {"model": ErrorResponse}, 503: {"model": ErrorResponse}},
)


class RawEvent(CamelModel):
    external_user_id: str
    external_item_id: str
    interaction_type: str
    timestamp_ms: int


class RawEventPage(CamelModel):
    data: list[RawEvent]
    next_cursor: str | None


class UserEvents(CamelModel):
    data: list[RawEvent]


def _epoch_ms(ts: datetime) -> int:
    # Stored as naive UTC.
    return int(ts.replace(tzinfo=timezone.utc).timestamp() * 1000)


def _raw(i: Interaction) -> RawEvent:
    return RawEvent(
        external_user_id=i.external_user_id,
        external_item_id=i.external_item_id,
        interaction_type=i.interaction_type.value,
        timestamp_ms=_epoch_ms(i.timestamp),
    )


def _encode_cursor(i: Interaction) -> str:
    return base64.urlsafe_b64encode(f"{i.timestamp.isoformat()}|{i.id}".encode()).decode()


def _decode_cursor(cursor: str) -> tuple[datetime, uuid.UUID]:
    try:
        ts, id_ = base64.urlsafe_b64decode(cursor.encode()).decode().split("|")
        return datetime.fromisoformat(ts), uuid.UUID(id_)
    except Exception:
        raise InvalidCursorError("Malformed cursor")


@router.get("/events", response_model=RawEventPage)
async def tenant_events(
    repository: RepositoryDep,
    tenant_id: Annotated[uuid.UUID, Path()],
    cursor: Annotated[str | None, Query()] = None,
    limit: Annotated[int, Query(ge=1, le=10000)] = 5000,
) -> RawEventPage:
    """All of a tenant's events in time order, keyset-paginated (stable while new events arrive)."""
    rows = await repository.tenant_events_after(tenant_id, _decode_cursor(cursor) if cursor else None, limit)
    return RawEventPage(
        data=[_raw(r) for r in rows],
        next_cursor=_encode_cursor(rows[-1]) if len(rows) == limit else None,
    )


@router.get("/users/{external_user_id}/events", response_model=UserEvents)
async def user_events(
    repository: RepositoryDep,
    tenant_id: Annotated[uuid.UUID, Path()],
    external_user_id: Annotated[str, Path(min_length=1, max_length=255)],
    limit: Annotated[int, Query(ge=1, le=10000)] = 5000,
) -> UserEvents:
    """The user's most recent events, oldest first."""
    rows = await repository.latest_user_events(tenant_id, external_user_id, limit)
    return UserEvents(data=[_raw(r) for r in rows])
