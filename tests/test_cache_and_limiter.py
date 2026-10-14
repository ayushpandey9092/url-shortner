import time

from app.cache import CachedURL, LRUCache
from app.rate_limit import TokenBucketLimiter


def entry(n: int) -> CachedURL:
    return CachedURL(n, f"https://example.com/{n}", None)


def test_lru_evicts_least_recently_used():
    cache = LRUCache(max_items=2, ttl_seconds=60)
    cache.set("a", entry(1))
    cache.set("b", entry(2))
    cache.get("a")  # "a" is now most recent
    cache.set("c", entry(3))
    assert cache.get("b") is None
    assert cache.get("a") == entry(1)
    assert cache.get("c") == entry(3)


def test_ttl_expiry(monkeypatch):
    cache = LRUCache(max_items=10, ttl_seconds=5)
    now = [1000.0]
    monkeypatch.setattr(time, "monotonic", lambda: now[0])
    cache.set("a", entry(1))
    now[0] += 4
    assert cache.get("a") is not None
    now[0] += 2
    assert cache.get("a") is None


def test_zero_size_cache_is_disabled():
    cache = LRUCache(max_items=0, ttl_seconds=60)
    cache.set("a", entry(1))
    assert cache.get("a") is None


def test_hit_and_miss_counters():
    cache = LRUCache(max_items=10, ttl_seconds=60)
    cache.get("x")
    cache.set("x", entry(1))
    cache.get("x")
    assert (cache.hits, cache.misses) == (1, 1)


def test_token_bucket_refills(monkeypatch):
    now = [0.0]
    monkeypatch.setattr(time, "monotonic", lambda: now[0])
    limiter = TokenBucketLimiter(per_minute=60)  # one token per second
    for _ in range(60):
        assert limiter.acquire("ip")[0]
    allowed, retry = limiter.acquire("ip")
    assert not allowed and retry == 1
    now[0] += 1.0
    assert limiter.acquire("ip")[0]


def test_token_bucket_is_per_client():
    limiter = TokenBucketLimiter(per_minute=1)
    assert limiter.acquire("a")[0]
    assert not limiter.acquire("a")[0]
    assert limiter.acquire("b")[0]
