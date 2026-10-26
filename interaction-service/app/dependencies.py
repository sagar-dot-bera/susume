import hashlib
import uuid
from typing import Annotated

from fastapi import Depends, Header, Request
from sqlalchemy.ext.asyncio import AsyncSession

from app.db import get_session
from app.errors import UnauthorizedError
from app.repository import InteractionRepository
from app.service import InteractionService

SessionDep = Annotated[AsyncSession, Depends(get_session)]


def get_repository(session: SessionDep) -> InteractionRepository:
    return InteractionRepository(session)


RepositoryDep = Annotated[InteractionRepository, Depends(get_repository)]


async def get_tenant_id(
    repository: RepositoryDep,
    x_api_key: Annotated[str | None, Header(alias="X-API-KEY")] = None,
) -> uuid.UUID:
    """Resolve the calling tenant from its API key, mirroring the backend's ApiKeyFilter."""
    if not x_api_key:
        raise UnauthorizedError("Invalid API key")

    key_hash = hashlib.sha256(x_api_key.encode("utf-8")).hexdigest()
    tenant_id = await repository.find_tenant_id_by_key_hash(key_hash)
    if tenant_id is None:
        raise UnauthorizedError("Invalid API key")
    return tenant_id


def get_service(request: Request, repository: RepositoryDep) -> InteractionService:
    return InteractionService(repository, request.app.state.cache)


TenantId = Annotated[uuid.UUID, Depends(get_tenant_id)]
ServiceDep = Annotated[InteractionService, Depends(get_service)]
