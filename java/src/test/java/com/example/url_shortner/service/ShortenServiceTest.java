package com.example.url_shortner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.url_shortner.storage.ShortUrl;
import com.example.url_shortner.service.AliasTakenException;
import com.example.url_shortner.storage.ShortUrlRepository;
import com.example.url_shortner.token.DeleteTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional // undo the database changes after each test
class ShortenServiceTest {

    @Autowired
    ShortenService shortenService;

    @Autowired
    ShortUrlRepository repository;

    @Test
    void shortenSavesTheLinkAndReturnsAShortUrlAndADeleteToken() {
        ShortenResult result = shortenService.shorten("https://example.com/some/long/path");

        // The short URL is the base URL followed by a 7-character code.
        String shortUrl = result.shortUrl();
        String code = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
        assertTrue(shortUrl.startsWith("http://localhost:8080/"));
        assertEquals(7, code.length());

        // The database keeps the code and the HASH of the token, never the token itself.
        ShortUrl saved = repository.findById(result.id()).orElseThrow();
        assertEquals("https://example.com/some/long/path", saved.originalUrl());
        assertEquals(code, saved.shortCode());
        assertEquals(DeleteTokens.hash(result.deleteToken()), saved.deleteTokenHash());
    }

    @Test
    void shortenRejectsAnInvalidUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> shortenService.shorten("javascript:alert(1)"));
    }

    @Test
    void aliasBecomesTheShortCode() {
        ShortenResult result = shortenService.shorten("https://example.com/a", "my-link");

        assertEquals("http://localhost:8080/my-link", result.shortUrl());
    }

    @Test
    void aliasThatIsTooShortIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> shortenService.shorten("https://example.com/a", "ab"));
    }

    @Test
    void aliasThatIsAlreadyTakenIsRejected() {
        shortenService.shorten("https://example.com/a", "taken-one");

        assertThrows(AliasTakenException.class,
                () -> shortenService.shorten("https://example.com/b", "taken-one"));
    }
}
