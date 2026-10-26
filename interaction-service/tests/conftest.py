import hashlib
import uuid
from collections.abc import AsyncIterator

import pytest
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncEngine, async_sessionmaker, create_async_engine
from sqlalchemy.pool import StaticPool

from app.config import Settings
from app.db import Base
from app.main import create_app
from app.models import ApiKey, Item

TENANT_A = uuid.UUID("00000000-0000-0000-0000-00000000000a")
TENANT_B = uuid.UUID("00000000-0000-0000-0000-00000000000b")
KEY_A = "tenant-a-key"
KEY_B = "tenant-b-key"


class FakeCache:
    def __init__(self) -> None:
        self.evicted: list[tuple[uuid.UUID, str]] = []

    async def evict(self, tenant_id: uuid.UUID, external_user_id: str) -> None:
        self.evicted.append((tenant_id, external_user_id))

    async def close(self) -> None:
        pass


def _hash(key: str) -> str:
    return hashlib.sha256(key.encode()).hexdigest()


@pytest.fixture
async def engine() -> AsyncIterator[AsyncEngine]:
    # A single shared in-memory SQLite connection stands in for Postgres.
    engine = create_async_engine("sqlite+aiosqlite://", poolclass=StaticPool)
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)

    async with async_sessionmaker(engine)() as session:
        session.add_all(
            [
                ApiKey(tenant_id=TENANT_A, key=_hash(KEY_A)),
                ApiKey(tenant_id=TENANT_B, key=_hash(KEY_B)),
                Item(tenant_id=TENANT_A, external_item_id="item-1", status="ACTIVE"),
                Item(tenant_id=TENANT_A, external_item_id="item-2", status="ACTIVE"),
                Item(tenant_id=TENANT_A, external_item_id="item-retired", status="INACTIVE"),
                Item(tenant_id=TENANT_B, external_item_id="item-b", status="ACTIVE"),
            ]
        )
        await session.commit()

    yield engine
    await engine.dispose()


@pytest.fixture
def cache() -> FakeCache:
    return FakeCache()


@pytest.fixture
async def client(engine: AsyncEngine, cache: FakeCache) -> AsyncIterator[AsyncClient]:
    app = create_app(Settings(database_url="sqlite+aiosqlite://"), engine=engine, cache=cache)
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as client:
        yield client


@pytest.fixture
def auth_a() -> dict[str, str]:
    return {"X-API-KEY": KEY_A}


@pytest.fixture
def auth_b() -> dict[str, str]:
    return {"X-API-KEY": KEY_B}
