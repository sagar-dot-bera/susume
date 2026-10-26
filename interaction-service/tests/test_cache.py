import re
import uuid

from app.cache import RedisRecommendationCache, escape_glob

TENANT = uuid.UUID("00000000-0000-0000-0000-00000000000a")


class FakeRedis:
    """Just enough of redis.asyncio.Redis: a dict with glob SCAN and DELETE."""

    def __init__(self, keys: list[str], fail: bool = False):
        self.store = dict.fromkeys(keys, b"{}")
        self.fail = fail

    async def scan_iter(self, match: str, count: int):
        if self.fail:
            raise ConnectionError("redis down")
        regex = re.compile(_glob_to_regex(match))
        for key in list(self.store):
            if regex.fullmatch(key):
                yield key

    async def delete(self, *keys: str) -> int:
        return sum(self.store.pop(k, None) is not None for k in keys)


def _glob_to_regex(pattern: str) -> str:
    out, i = [], 0
    while i < len(pattern):
        ch = pattern[i]
        if ch == "\\":
            i += 1
            out.append(re.escape(pattern[i]))
        elif ch == "*":
            out.append(".*")
        elif ch == "?":
            out.append(".")
        else:
            out.append(re.escape(ch))
        i += 1
    return "".join(out)


async def test_evicts_every_limit_for_the_user_only():
    redis = FakeRedis(
        [
            f"recommendation::{TENANT}:user-1:5",
            f"recommendation::{TENANT}:user-1:20",
            f"recommendation::{TENANT}:user-10:5",  # shares a prefix with user-1, must survive
            f"recommendation::{TENANT}:user-2:5",
        ]
    )

    await RedisRecommendationCache(redis).evict(TENANT, "user-1")

    assert sorted(redis.store) == [f"recommendation::{TENANT}:user-10:5", f"recommendation::{TENANT}:user-2:5"]


async def test_glob_characters_in_user_id_do_not_widen_the_match():
    redis = FakeRedis([f"recommendation::{TENANT}:user-1:5", f"recommendation::{TENANT}:*:5"])

    await RedisRecommendationCache(redis).evict(TENANT, "*")

    assert list(redis.store) == [f"recommendation::{TENANT}:user-1:5"]


async def test_redis_failure_is_swallowed():
    await RedisRecommendationCache(FakeRedis([], fail=True)).evict(TENANT, "user-1")


def test_escape_glob():
    assert escape_glob("a*b?c[d]e\\") == "a\\*b\\?c\\[d\\]e\\\\"
