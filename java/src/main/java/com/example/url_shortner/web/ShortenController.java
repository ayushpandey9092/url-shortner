package com.example.url_shortner.web;

import com.example.url_shortner.service.ShortenResult;
import com.example.url_shortner.service.ShortenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles HTTP requests for creating short links.
 */
@RestController
public class ShortenController {

    private final ShortenService shortenService;
    private final RateLimiter rateLimiter;

    public ShortenController(ShortenService shortenService, RateLimiter rateLimiter) {
        this.shortenService = shortenService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * The JSON body the client sends, for example:
     * {"url": "https://...", "custom_alias": "my-link", "expires_at": "2027-01-01T00:00:00Z"}.
     * The last two fields are optional.
     */
    public record ShortenRequest(String url, String customAlias, String expiresAt) {
    }

    // POST /api/shorten: create a short link. 201 Created means something new was made.
    @PostMapping("/api/shorten")
    public ResponseEntity<Object> shorten(@RequestBody ShortenRequest request,
                                          HttpServletRequest http) {
        // Too many links from this address in one minute: 429 Too Many Requests.
        if (!rateLimiter.allow(http.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorBody("Too many links created. Please wait a minute."));
        }

        ShortenResult result = shortenService.shorten(
                request.url(), request.customAlias(), request.expiresAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
