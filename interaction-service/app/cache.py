import logging
import secrets
import uuid
from typing import Protocol

from redis.asyncio import Redis

logger = logging.getLogger(__name__)

DEFAULT_GENERATION_TTL_SECONDS = 24 * 60 * 60


class RecommendationCache(Protocol):
    async def evict(self, tenant_id: uuid.UUID, external_user_id: str) -> None: ...

    async def close(self) -> None: ...


def user_generation_key(tenant_id: uuid.UUID, external_user_id: str) -> str:
    """Must match RecommendationCacheKeys.userGeneration in the backend."""
    return f"reco:{{{tenant_id}}}:user:{external_user_id}:gen"


class RedisRecommendationCache:
    """
    Invalidates a user's cached recommendations in the backend's cache.

    The backend includes a per-user generation token in every personalized cache key
    (see backend RecommendationCacheKeys), so replacing the token makes all of the user's
    cached lists unreachable in one write; they expire on their own TTL. The token is
    random, and its TTL must outlive the backend's entry TTLs (same value as the backend's
    recommendation.cache.generation-ttl).
    """

    def __init__(self, redis: Redis, generation_ttl_seconds: int = DEFAULT_GENERATION_TTL_SECONDS):
        self._redis = redis
        self._generation_ttl_seconds = generation_ttl_seconds

    @classmethod
    def from_url(
        cls, url: str, generation_ttl_seconds: int = DEFAULT_GENERATION_TTL_SECONDS
    ) -> "RedisRecommendationCache":
        return cls(Redis.from_url(url), generation_ttl_seconds)

    async def evict(self, tenant_id: uuid.UUID, external_user_id: str) -> None:
        key = user_generation_key(tenant_id, external_user_id)
        try:
            await self._redis.set(key, secrets.token_hex(8), ex=self._generation_ttl_seconds)
        except Exception:
            # Stale entries expire on their own TTL; don't fail the write over it.
            logger.warning("Failed to invalidate recommendation cache for %s", key, exc_info=True)

    async def close(self) -> None:
        await self._redis.aclose()


class NoOpRecommendationCache:
    async def evict(self, tenant_id: uuid.UUID, external_user_id: str) -> None:
        return None

    async def close(self) -> None:
        return None
