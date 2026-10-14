"""Per-client token-bucket rate limiter for link creation."""

import math
import threading
import time
from dataclasses import dataclass

from fastapi import HTTPException, Request, status


@dataclass
class _Bucket:
    tokens: float
    updated_at: float


class TokenBucketLimiter:
    """Each client gets `capacity` tokens that refill evenly over a minute.

    A token bucket allows short bursts (up to the full capacity) while holding
    the long-run rate to `per_minute`, which suits people pasting a few links
    at once but stops scripts from flooding the database.
    """

    def __init__(self, per_minute: int) -> None:
        self.capacity = float(per_minute)
        self.refill_per_second = per_minute / 60.0
        self._buckets: dict[str, _Bucket] = {}
        self._lock = threading.Lock()
        self._last_sweep = time.monotonic()

    @property
    def enabled(self) -> bool:
        return self.capacity > 0

    def acquire(self, key: str) -> tuple[bool, int]:
        """Take one token. Returns (allowed, seconds_until_next_token)."""
        now = time.monotonic()
        with self._lock:
            self._sweep(now)
            bucket = self._buckets.get(key)
            if bucket is None:
                bucket = self._buckets[key] = _Bucket(self.capacity, now)
            else:
                elapsed = now - bucket.updated_at
                bucket.tokens = min(self.capacity, bucket.tokens + elapsed * self.refill_per_second)
                bucket.updated_at = now
            if bucket.tokens >= 1:
                bucket.tokens -= 1
                return True, 0
            wait = (1 - bucket.tokens) / self.refill_per_second
            return False, max(1, math.ceil(wait))

    def _sweep(self, now: float) -> None:
        """Drop buckets that have refilled completely, so memory stays bounded."""
        if now - self._last_sweep < 60:
            return
        self._last_sweep = now
        full_after = self.capacity / self.refill_per_second
        stale = [k for k, b in self._buckets.items() if now - b.updated_at > full_after]
        for key in stale:
            del self._buckets[key]


def client_ip(request: Request) -> str:
    if request.app.state.settings.trust_proxy_headers:
        forwarded = request.headers.get("x-forwarded-for")
        if forwarded:
            return forwarded.split(",")[0].strip()
    return request.client.host if request.client else "unknown"


def enforce_rate_limit(request: Request) -> None:
    """FastAPI dependency that rejects over-limit clients with 429."""
    limiter: TokenBucketLimiter = request.app.state.rate_limiter
    if not limiter.enabled:
        return
    allowed, retry_after = limiter.acquire(client_ip(request))
    if not allowed:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Rate limit exceeded. Try again shortly.",
            headers={"Retry-After": str(retry_after)},
        )
