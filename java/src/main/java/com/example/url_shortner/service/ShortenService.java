package com.example.url_shortner.service;

import com.example.url_shortner.encoding.CodeGenerator;
import com.example.url_shortner.storage.ShortUrlRepository;
import com.example.url_shortner.token.DeleteTokens;
import com.example.url_shortner.validation.AliasValidator;
import com.example.url_shortner.validation.UrlValidator;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates a short link for a long URL.
 */
@Service
public class ShortenService {

    // These settings must never change once links exist. See app/config.py in the Python version.
    private static final int CODE_LENGTH = 7;
    private static final long MULTIPLIER = 2_176_477_521_981L;
    private static final long OFFSET = 1_094_865_271L;

    private static final int MAX_URL_LENGTH = 2048;

    private final ShortUrlRepository repository;
    private final CodeGenerator codeGenerator;
    private final String baseUrl;
    private final String ownHost;
    private final int maxExpiryDays;

    public ShortenService(ShortUrlRepository repository,
                          @Value("${app.base-url}") String baseUrl,
                          @Value("${app.max-expiry-days}") int maxExpiryDays) {
        this.repository = repository;
        this.codeGenerator = new CodeGenerator(CODE_LENGTH, MULTIPLIER, OFFSET);
        this.baseUrl = baseUrl;
        this.maxExpiryDays = maxExpiryDays;
        // The host of our own site, so we can refuse to shorten our own links.
        this.ownHost = URI.create(baseUrl).getHost();
    }

    /** Shortens one URL, with a generated code and no expiry. */
    public ShortenResult shorten(String rawUrl) {
        return shorten(rawUrl, null, null);
    }

    /** Shortens one URL with an optional alias, and no expiry. */
    public ShortenResult shorten(String rawUrl, String alias) {
        return shorten(rawUrl, alias, null);
    }

    /**
     * Shortens one URL.
     *
     * @param alias        if not null, the alias becomes the short code
     * @param expiresAtText if not null, the time the link stops working, such as 2027-01-01T00:00:00Z
     * @throws IllegalArgumentException if the URL, alias or expiry is not allowed
     * @throws AliasTakenException      if someone already uses the alias
     */
    public ShortenResult shorten(String rawUrl, String alias, String expiresAtText) {
        // Step 1: check the URL and tidy it up (for example, add https:// if missing).
        String url = UrlValidator.normalizeAndValidate(rawUrl, MAX_URL_LENGTH, ownHost);

        // Step 2: check the expiry time, if one was given.
        String expiresAt = parseExpiry(expiresAtText);

        // Step 3: make a delete token. We keep only its hash in the database.
        String deleteToken = DeleteTokens.newToken();

        if (alias != null) {
            return shortenWithAlias(url, alias, expiresAt, deleteToken);
        }

        // Step 4: save the row. The database gives it an ID, but no short code yet.
        long id = repository.save(url, DeleteTokens.hash(deleteToken), expiresAt);

        // Step 5: make the short code from the ID, and write it into the same row.
        String code = codeGenerator.encode(id);
        repository.updateShortCode(id, code);

        // Step 6: build the full short link and return everything the caller needs.
        return new ShortenResult(id, code, baseUrl + "/" + code, deleteToken, expiresAt);
    }

    // Same steps as above, but the user's alias is the code instead of a generated one.
    private ShortenResult shortenWithAlias(String url, String alias, String expiresAt,
                                           String deleteToken) {
        String cleanAlias = AliasValidator.validate(alias);

        // Check first, so the user gets a clear message instead of a database error.
        if (repository.findByShortCode(cleanAlias).isPresent()) {
            throw new AliasTakenException(cleanAlias);
        }

        long id = repository.save(url, DeleteTokens.hash(deleteToken), expiresAt);
        repository.updateShortCode(id, cleanAlias);
        return new ShortenResult(id, cleanAlias, baseUrl + "/" + cleanAlias, deleteToken,
                expiresAt);
    }

    // Returns the expiry time in a standard text form, or null if none was given.
    private String parseExpiry(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Instant expiresAt;
        try {
            expiresAt = Instant.parse(text.strip());
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException(
                    "expires_at must be an ISO 8601 time ending in Z, "
                            + "for example 2027-01-01T00:00:00Z.");
        }

        Instant now = Instant.now();
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expires_at must be in the future.");
        }
        if (expiresAt.isAfter(now.plus(Duration.ofDays(maxExpiryDays)))) {
            throw new IllegalArgumentException(
                    "expires_at can be at most " + maxExpiryDays + " days from now.");
        }
        return expiresAt.toString();
    }
}
