package com.example.url_shortner.cache;

import com.example.url_shortner.storage.ShortUrl;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * A small memory cache for the links people visit most.
 *
 * <p>Looking up a link in memory is faster than asking the database every time.
 * When the cache is full, the least recently used link is dropped.
 *
 * <p>Every method is synchronized, so two requests cannot change the map at the same time.
 */
@Component
public class LinkCache {

    private static final int MAX_ITEMS = 1000;

    // LinkedHashMap with accessOrder=true keeps items in the order they were last used.
    private final Map<String, ShortUrl> items =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ShortUrl> eldest) {
                    // Drop the least recently used item once the cache is too full.
                    return size() > MAX_ITEMS;
                }
            };

    /** Returns the cached link for this code, or null if it is not cached. */
    public synchronized ShortUrl get(String code) {
        return items.get(code);
    }

    /** Stores a link under its code. */
    public synchronized void put(String code, ShortUrl link) {
        items.put(code, link);
    }

    /** Forgets a link, for example after it is deleted. */
    public synchronized void remove(String code) {
        items.remove(code);
    }

    /** Forgets every link. */
    public synchronized void clear() {
        items.clear();
    }
}
