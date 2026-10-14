"""In-memory LRU cache with per-entry TTL for short-code lookups.

Redirects are read-heavy and follow a power law: a small set of links gets most
of the traffic. Caching the code -> destination mapping skips the database on
those hot paths. The class exposes get/set/delete so it can be swapped for a
Redis-backed cache when the app runs on more than one instance.
"""

import threading
import time
from collections import OrderedDict
from dataclasses import dataclass
from datetime import datetime


@dataclass(frozen=True)
class CachedURL:
    url_id: int
    original_url: str
    expires_at: datetime | None


class LRUCache:
    def __init__(self, max_items: int, ttl_seconds: int) -> None:
        self.max_items = max_items
        self.ttl_seconds = ttl_seconds
        self._data: OrderedDict[str, tuple[float, CachedURL]] = OrderedDict()
        self._lock = threading.Lock()
        self.hits = 0
        self.misses = 0

    def get(self, key: str) -> CachedURL | None:
        if self.max_items == 0:
            return None
        with self._lock:
            item = self._data.get(key)
            if item is None:
                self.misses += 1
                return None
            stored_at, value = item
            if time.monotonic() - stored_at > self.ttl_seconds:
                del self._data[key]
                self.misses += 1
                return None
            self._data.move_to_end(key)
            self.hits += 1
            return value

    def set(self, key: str, value: CachedURL) -> None:
        if self.max_items == 0:
            return
        with self._lock:
            self._data[key] = (time.monotonic(), value)
            self._data.move_to_end(key)
            while len(self._data) > self.max_items:
                self._data.popitem(last=False)

    def delete(self, key: str) -> None:
        with self._lock:
            self._data.pop(key, None)

    def clear(self) -> None:
        with self._lock:
            self._data.clear()

    def __len__(self) -> int:
        return len(self._data)
