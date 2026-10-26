import logging
import re
import uuid
from typing import Protocol

from redis.asyncio import Redis

logger = logging.getLogger(__name__)


class RecommendationCache(Protocol):
    async def evict(self, tenant_id: uuid.UUID, external_user_id: str) -> None: ...

    async def close(self) -> None: ...


def escape_glob(value: str) -> str:
    """Escape Redis glob metacharacters so a user id can't widen the pattern."""
    return re.sub(r"([*?\[\]\\])", r"\\\1", value)


class RedisRecommendationCache:
    """
    Evicts a user's cached recommendations from the backend's Spring cache.

    Spring's RedisCacheManager stores entries as "<cacheName>::<key>", and the backend
    keys the "recommendation" cache by "<tenantId>:<externalUserId>:<limit>", so every
    limit cached for the user is deleted (same pattern as RecommendationCacheEvictor).
    """

    def __init__(self, redis: Redis):
        self._redis = redis

    @classmethod
    def from_url(cls, url: str) -> "RedisRecommendationCache":
        return cls(Redis.from_url(url))

    async def evict(self, tenant_id: uuid.UUID, external_user_id: str) -> None:
        pattern = f"recommendation::{escape_glob(f'{tenant_id}:{external_user_id}:')}*"
        try:
            keys = [key async for key in self._redis.scan_iter(match=pattern, count=1000)]
            if keys:
                await self._redis.delete(*keys)
        except Exception:
            # Stale entries expire on their own (15 min TTL); don't fail the write over it.
            logger.warning("Failed to evict recommendation cache keys %s", pattern, exc_info=True)

    async def close(self) -> None:
        await self._redis.aclose()


class NoOpRecommendationCache:
    async def evict(self, tenant_id: uuid.UUID, external_user_id: str) -> None:
        return None

    async def close(self) -> None:
        return None
