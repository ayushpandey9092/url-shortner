package com.example.url_shortner.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UrlValidatorTest {

    private static final int MAX = 2048;
    private static final String OWN_HOST = "sho.rt";

    @Test
    void addsHttpsWhenSchemeIsMissing() {
        assertEquals("https://example.com/page",
                UrlValidator.normalizeAndValidate("example.com/page", MAX, OWN_HOST));
    }

    @Test
    void keepsAValidHttpUrlAsItIs() {
        assertEquals("http://example.com/a?b=1",
                UrlValidator.normalizeAndValidate("http://example.com/a?b=1", MAX, OWN_HOST));
    }

    @Test
    void rejectsJavascriptScheme() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.normalizeAndValidate("javascript:alert(1)", MAX, OWN_HOST));
    }

    @Test
    void rejectsWhitespaceInsideTheUrl() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.normalizeAndValidate("https://exa mple.com", MAX, OWN_HOST));
    }

    @Test
    void rejectsLocalhost() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.normalizeAndValidate("http://localhost:8080", MAX, OWN_HOST));
    }

    @Test
    void rejectsTheShortenersOwnHost() {
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.normalizeAndValidate("https://sho.rt/W9eTrsF", MAX, OWN_HOST));
    }

    @Test
    void rejectsUrlLongerThanTheMaximum() {
        String longUrl = "https://example.com/" + "a".repeat(100);
        assertThrows(IllegalArgumentException.class,
                () -> UrlValidator.normalizeAndValidate(longUrl, 50, OWN_HOST));
    }
}
