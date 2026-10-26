import uuid
from datetime import datetime

from sqlalchemy import Select, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import ApiKey, Interaction, InteractionType, Item


class InteractionRepository:
    def __init__(self, session: AsyncSession):
        self._session = session

    async def find_tenant_id_by_key_hash(self, key_hash: str) -> uuid.UUID | None:
        return await self._session.scalar(select(ApiKey.tenant_id).where(ApiKey.key == key_hash))

    async def find_item(self, tenant_id: uuid.UUID, external_item_id: str) -> Item | None:
        return await self._session.scalar(
            select(Item).where(Item.tenant_id == tenant_id, Item.external_item_id == external_item_id)
        )

    async def add(self, interaction: Interaction) -> Interaction:
        self._session.add(interaction)
        await self._session.commit()
        await self._session.refresh(interaction)
        return interaction

    async def page_user_history(
        self,
        tenant_id: uuid.UUID,
        external_user_id: str,
        interaction_type: InteractionType | None,
        since: datetime | None,
        offset: int,
        limit: int,
    ) -> tuple[list[Interaction], int]:
        stmt = select(Interaction).where(
            Interaction.tenant_id == tenant_id,
            Interaction.external_user_id == external_user_id,
        )
        if interaction_type is not None:
            stmt = stmt.where(Interaction.interaction_type == interaction_type)
        if since is not None:
            stmt = stmt.where(Interaction.timestamp >= since)

        stmt = stmt.order_by(Interaction.timestamp.desc(), Interaction.id)
        return await self._page(stmt, offset, limit)

    async def page_tenant_export(self, tenant_id: uuid.UUID, offset: int, limit: int) -> tuple[list[Interaction], int]:
        # Ascending with an id tiebreaker so pages stay stable while the training pipeline walks them.
        stmt = (
            select(Interaction)
            .where(Interaction.tenant_id == tenant_id)
            .order_by(Interaction.timestamp.asc(), Interaction.id)
        )
        return await self._page(stmt, offset, limit)

    async def tenant_events_after(
        self, tenant_id: uuid.UUID, after: tuple[datetime, uuid.UUID] | None, limit: int
    ) -> list[Interaction]:
        """Keyset page of a tenant's events in (timestamp, id) order, starting after `after`."""
        stmt = select(Interaction).where(Interaction.tenant_id == tenant_id)
        if after is not None:
            ts, id_ = after
            stmt = stmt.where(
                (Interaction.timestamp > ts) | ((Interaction.timestamp == ts) & (Interaction.id > id_))
            )
        stmt = stmt.order_by(Interaction.timestamp, Interaction.id).limit(limit)
        return list(await self._session.scalars(stmt))

    async def latest_user_events(self, tenant_id: uuid.UUID, external_user_id: str, limit: int) -> list[Interaction]:
        """The user's most recent `limit` events, oldest first."""
        stmt = (
            select(Interaction)
            .where(Interaction.tenant_id == tenant_id, Interaction.external_user_id == external_user_id)
            .order_by(Interaction.timestamp.desc(), Interaction.id.desc())
            .limit(limit)
        )
        return list(reversed(list(await self._session.scalars(stmt))))

    async def _page(self, stmt: Select, offset: int, limit: int) -> tuple[list[Interaction], int]:
        total = await self._session.scalar(select(func.count()).select_from(stmt.order_by(None).subquery()))
        rows = await self._session.scalars(stmt.offset(offset).limit(limit))
        return list(rows), total or 0
