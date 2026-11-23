package com.example.url_shortner.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional // rolls back each test's changes, so the database file stays clean
class ShortUrlRepositoryTest {

    @Autowired
    ShortUrlRepository repository;

    @Test
    void savedLinkCanBeReadBackById() {
        long id = repository.save("https://example.com/some/long/path", "hash-abc");

        ShortUrl found = repository.findById(id).orElseThrow();

        assertEquals("https://example.com/some/long/path", found.originalUrl());
        assertEquals("hash-abc", found.deleteTokenHash());
        assertTrue(found.isActive());
        assertEquals(0, found.clickCount());
    }
}
