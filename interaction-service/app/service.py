import logging
import math
import uuid
from datetime import datetime, timezone

from app.cache import RecommendationCache
from app.errors import ItemInactiveError, ItemNotFoundError
from app.models import Interaction, InteractionType
from app.repository import InteractionRepository
from app.schemas import InteractionHistoryItem, InteractionResponse, Page, RecordInteractionRequest

logger = logging.getLogger(__name__)


def to_naive_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value
    return value.astimezone(timezone.utc).replace(tzinfo=None)


class InteractionService:
    def __init__(self, repository: InteractionRepository, cache: RecommendationCache):
        self._repository = repository
        self._cache = cache

    async def record(self, tenant_id: uuid.UUID, request: RecordInteractionRequest) -> InteractionResponse:
        item = await self._repository.find_item(tenant_id, request.external_item_id)
        if item is None:
            raise ItemNotFoundError(f"Item '{request.external_item_id}' not found")
        if item.status != "ACTIVE":
            raise ItemInactiveError(f"Item '{request.external_item_id}' is not active")

        interaction = await self._repository.add(
            Interaction(
                tenant_id=tenant_id,
                external_user_id=request.external_user_id,
                external_item_id=request.external_item_id,
                interaction_type=request.interaction_type,
            )
        )
        logger.info("Interaction recorded: %s for tenant: %s", interaction.id, tenant_id)

        # The user's behaviour changed, so their cached recommendations are stale.
        await self._cache.evict(tenant_id, request.external_user_id)
        return InteractionResponse.model_validate(interaction)

    async def user_history(
        self,
        tenant_id: uuid.UUID,
        external_user_id: str,
        interaction_type: InteractionType | None,
        since: datetime | None,
        page: int,
        limit: int,
    ) -> Page[InteractionHistoryItem]:
        rows, total = await self._repository.page_user_history(
            tenant_id,
            external_user_id,
            interaction_type,
            to_naive_utc(since) if since else None,
            offset=page * limit,
            limit=limit,
        )
        return _page([InteractionHistoryItem.model_validate(r) for r in rows], page, limit, total)

    async def export(self, tenant_id: uuid.UUID, page: int, limit: int) -> Page[InteractionResponse]:
        rows, total = await self._repository.page_tenant_export(tenant_id, offset=page * limit, limit=limit)
        return _page([InteractionResponse.model_validate(r) for r in rows], page, limit, total)


def _page(data: list, page: int, limit: int, total: int) -> Page:
    return Page(data=data, page=page, limit=limit, total_elements=total, total_pages=math.ceil(total / limit))
