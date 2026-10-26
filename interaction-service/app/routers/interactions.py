from datetime import datetime
from typing import Annotated

from fastapi import APIRouter, Query, status

from app.dependencies import ServiceDep, TenantId
from app.models import InteractionType
from app.schemas import (
    ErrorResponse,
    InteractionHistoryItem,
    InteractionResponse,
    Page,
    RecordInteractionRequest,
)

router = APIRouter(
    prefix="/api/v1/interactions",
    tags=["Interactions"],
    responses={401: {"model": ErrorResponse, "description": "Missing or invalid X-API-KEY"}},
)


@router.post(
    "",
    response_model=InteractionResponse,
    status_code=status.HTTP_201_CREATED,
    responses={404: {"model": ErrorResponse}, 409: {"model": ErrorResponse}},
)
async def record_interaction(
    request: RecordInteractionRequest, tenant_id: TenantId, service: ServiceDep
) -> InteractionResponse:
    """Record a user interaction with an active item of the calling tenant."""
    return await service.record(tenant_id, request)


@router.get("/history", response_model=Page[InteractionHistoryItem])
async def get_user_history(
    tenant_id: TenantId,
    service: ServiceDep,
    external_user_id: Annotated[str, Query(alias="externalUserId", min_length=1, max_length=255)],
    interaction_type: Annotated[InteractionType | None, Query(alias="interactionType")] = None,
    since: Annotated[datetime | None, Query(description="ISO-8601; only interactions at or after this time")] = None,
    page: Annotated[int, Query(ge=0)] = 0,
    limit: Annotated[int, Query(ge=1, le=100)] = 20,
) -> Page[InteractionHistoryItem]:
    """A user's interactions, newest first, optionally filtered by type and start time."""
    return await service.user_history(tenant_id, external_user_id, interaction_type, since, page, limit)


@router.get("/export", response_model=Page[InteractionResponse])
async def export_interactions(
    tenant_id: TenantId,
    service: ServiceDep,
    page: Annotated[int, Query(ge=0)] = 0,
    limit: Annotated[int, Query(ge=1, le=1000)] = 500,
) -> Page[InteractionResponse]:
    """All of the tenant's interactions, oldest first, for the offline training pipeline."""
    return await service.export(tenant_id, page, limit)
