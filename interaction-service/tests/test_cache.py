import uuid

from app.cache import RedisRecommendationCache, user_generation_key

TENANT = uuid.UUID("00000000-0000-0000-0000-00000000000a")


class FakeRedis:
    """Just enough of redis.asyncio.Redis: SET with an expiry."""

    def __init__(self, fail: bool = False):
        self.store: dict[str, str] = {}
        self.expiry: dict[str, int] = {}
        self.fail = fail

    async def set(self, key: str, value: str, ex: int | None = None) -> bool:
        if self.fail:
            raise ConnectionError("redis down")
        self.store[key] = value
        self.expiry[key] = ex
        return True


async def test_evict_replaces_the_users_generation_token():
    redis = FakeRedis()
    cache = RedisRecommendationCache(redis, generation_ttl_seconds=3600)

    await cache.evict(TENANT, "user-1")
    first = redis.store[f"reco:{{{TENANT}}}:user:user-1:gen"]
    await cache.evict(TENANT, "user-1")

    assert redis.store[f"reco:{{{TENANT}}}:user:user-1:gen"] != first
    assert redis.expiry[f"reco:{{{TENANT}}}:user:user-1:gen"] == 3600
    assert list(redis.store) == [f"reco:{{{TENANT}}}:user:user-1:gen"]


async def test_redis_failure_is_swallowed():
    await RedisRecommendationCache(FakeRedis(fail=True)).evict(TENANT, "user-1")


def test_key_matches_backend_format():
    # Same literal as RecommendationCacheTest.keyFormatMatchesInteractionService in the backend.
    assert user_generation_key(TENANT, "user-1") == f"reco:{{{TENANT}}}:user:user-1:gen"
