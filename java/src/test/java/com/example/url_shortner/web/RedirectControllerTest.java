package com.example.url_shortner.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.url_shortner.cache.LinkCache;
import com.example.url_shortner.service.ShortenResult;
import com.example.url_shortner.service.ShortenService;
import com.example.url_shortner.storage.ShortUrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // undo the database changes after each test
class RedirectControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ShortenService shortenService;

    @Autowired
    ShortUrlRepository repository;

    @Autowired
    LinkCache linkCache;

    @BeforeEach
    void clearCache() {
        // Rolled-back test data can reuse IDs and codes, so start each test with an empty cache.
        linkCache.clear();
    }

    @Test
    void visitingAShortLinkRedirectsToTheOriginalUrl() throws Exception {
        ShortenResult made = shortenService.shorten("https://example.com/some/long/path");

        mockMvc.perform(get("/" + made.shortCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/some/long/path"));
    }

    @Test
    void unknownCodeReturns404() throws Exception {
        mockMvc.perform(get("/zzzzzzz"))
                .andExpect(status().isNotFound());
    }

    @Test
    void eachVisitAddsOneToTheClickCount() throws Exception {
        ShortenResult made = shortenService.shorten("https://example.com/some/long/path");

        mockMvc.perform(get("/" + made.shortCode()));
        mockMvc.perform(get("/" + made.shortCode()));

        assertEquals(2, repository.findById(made.id()).orElseThrow().clickCount());
    }
}
