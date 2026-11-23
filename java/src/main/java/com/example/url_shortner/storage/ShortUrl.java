package com.example.url_shortner.storage;

/**
 * One row of the short_urls table, as a Java object.
 *
 * <p>expiresAt is null for links that never expire. Times are stored as text.
 */
public record ShortUrl(
        long id,
        String originalUrl,
        String shortCode,
        String deleteTokenHash,
        boolean isActive,
        long clickCount,
        String expiresAt,
        String createdAt) {
}
