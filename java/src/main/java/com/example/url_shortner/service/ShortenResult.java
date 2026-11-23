package com.example.url_shortner.service;

/**
 * What the shorten service gives back to the caller.
 *
 * @param id          the row ID in the database
 * @param shortCode   the code part of the link, such as W9eTrsF
 * @param shortUrl    the full short link, such as http://localhost:8080/W9eTrsF
 * @param deleteToken the token the creator needs to delete the link. Shown only once.
 * @param expiresAt   when the link expires, or null if it never does
 */
public record ShortenResult(
        long id,
        String shortCode,
        String shortUrl,
        String deleteToken,
        String expiresAt) {
}
