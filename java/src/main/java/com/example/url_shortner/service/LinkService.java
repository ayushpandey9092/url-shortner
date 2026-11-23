package com.example.url_shortner.service;

import com.example.url_shortner.cache.LinkCache;
import com.example.url_shortner.storage.ShortUrl;
import com.example.url_shortner.storage.ShortUrlRepository;
import com.example.url_shortner.token.DeleteTokens;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Works with existing links: visits, statistics and deletion.
 */
@Service
public class LinkService {

    private final ShortUrlRepository repository;
    private final LinkCache cache;
    private final String baseUrl;

    public LinkService(ShortUrlRepository repository, LinkCache cache,
                       @Value("${app.base-url}") String baseUrl) {
        this.repository = repository;
        this.cache = cache;
        this.baseUrl = baseUrl;
    }

    /**
     * Finds a link for a visit. Checks the cache first, then the database.
     * Returns empty if the link does not exist or was deleted.
     */
    public Optional<ShortUrl> findForVisit(String code) {
        ShortUrl cached = cache.get(code);
        if (cached != null) {
            return Optional.of(cached);
        }

        Optional<ShortUrl> found = repository.findByShortCode(code);
        if (found.isPresent() && found.get().isActive()) {
            cache.put(code, found.get());
            return found;
        }
        return Optional.empty();
    }

    /** True if the link has an expiry time and that time has passed. */
    public boolean isExpired(ShortUrl link) {
        return link.expiresAt() != null && Instant.parse(link.expiresAt()).isBefore(Instant.now());
    }

    /** Counts one visit: adds a clicks row and increases the total. */
    public void recordVisit(ShortUrl link, String referrer, String userAgent) {
        repository.recordClick(link.id(), referrer, userAgent);
    }

    /** Builds the statistics for one link. */
    public StatsResult stats(String code) {
        ShortUrl link = findActiveOrThrow(code);

        List<StatsResult.DayCount> days = new ArrayList<>();
        for (Map<String, Object> row : repository.clicksPerDayLastWeek(link.id())) {
            days.add(new StatsResult.DayCount(
                    (String) row.get("day"),
                    ((Number) row.get("clicks")).longValue()));
        }

        List<StatsResult.ReferrerCount> referrers = new ArrayList<>();
        for (Map<String, Object> row : repository.topReferrers(link.id())) {
            referrers.add(new StatsResult.ReferrerCount(
                    (String) row.get("referrer"),
                    ((Number) row.get("clicks")).longValue()));
        }

        return new StatsResult(
                link.shortCode(),
                baseUrl + "/" + link.shortCode(),
                link.originalUrl(),
                link.createdAt(),
                link.expiresAt(),
                isExpired(link),
                link.clickCount(),
                repository.lastClickTime(link.id()),
                days,
                referrers);
    }

    /**
     * Deletes a link. The caller must send the delete token that was given when the link
     * was created. We compare its hash with the stored hash.
     */
    public void delete(String code, String token) {
        ShortUrl link = findActiveOrThrow(code);

        if (token == null || !DeleteTokens.hash(token).equals(link.deleteTokenHash())) {
            throw new WrongTokenException();
        }

        repository.deactivate(link.id());
        // Remove it from the cache too, so visits stop working straight away.
        cache.remove(code);
    }

    // Returns the link if it exists and is not deleted. Otherwise throws NotFoundException.
    private ShortUrl findActiveOrThrow(String code) {
        Optional<ShortUrl> found = repository.findByShortCode(code);
        if (found.isEmpty() || !found.get().isActive()) {
            throw new NotFoundException("Link not found.");
        }
        return found.get();
    }
}
