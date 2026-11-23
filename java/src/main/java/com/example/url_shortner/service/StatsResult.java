package com.example.url_shortner.service;

import java.util.List;

/**
 * The statistics for one link, as returned by GET /api/urls/{code}/stats.
 */
public record StatsResult(
        String shortCode,
        String shortUrl,
        String originalUrl,
        String createdAt,
        String expiresAt,
        boolean isExpired,
        long totalClicks,
        String lastAccessedAt,
        List<DayCount> clicksLast7Days,
        List<ReferrerCount> topReferrers) {

    /** How many clicks happened on one day. */
    public record DayCount(String date, long clicks) {
    }

    /** How many clicks came from one referrer. */
    public record ReferrerCount(String referrer, long clicks) {
    }
}
