package com.example.url_shortner.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.url_shortner.cache.LinkCache;
import com.example.url_shortner.service.ShortenResult;
import com.example.url_shortner.service.ShortenService;
import com.example.url_shortner.storage.ShortUrlRepository;
import com.example.url_shortner.token.DeleteTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tests for expiry, deletion, statistics and the web page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FeatureControllerTest {

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
        linkCache.clear();
    }

    @Test
    void deleteNeedsTheRightTokenAndThenTheLinkStopsWorking() throws Exception {
        ShortenResult made = shortenService.shorten("https://example.com/delete-me");
        String code = made.shortCode();

        // A wrong token is refused.
        mockMvc.perform(delete("/api/urls/" + code)
                        .header("X-Delete-Token", "not-the-token"))
                .andExpect(status().isForbidden());

        // The right token deletes the link.
        mockMvc.perform(delete("/api/urls/" + code)
                        .header("X-Delete-Token", made.deleteToken()))
                .andExpect(status().isNoContent());

        // Visiting it now gives 404, even though it was cached before.
        mockMvc.perform(get("/" + code))
                .andExpect(status().isNotFound());
    }

    @Test
    void statsCountTheVisits() throws Exception {
        ShortenResult made = shortenService.shorten("https://example.com/stats-me");
        String code = made.shortCode();

        mockMvc.perform(get("/" + code));
        mockMvc.perform(get("/" + code));

        mockMvc.perform(get("/api/urls/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_clicks").value(2))
                .andExpect(jsonPath("$.original_url").value("https://example.com/stats-me"));
    }

    @Test
    void expiredLinkReturns410Gone() throws Exception {
        // Save a link that expired in the year 2000, directly in the database.
        long id = repository.save("https://example.com/old", DeleteTokens.hash("x"),
                "2000-01-01T00:00:00Z");
        repository.updateShortCode(id, "oldlink1");

        mockMvc.perform(get("/oldlink1"))
                .andExpect(status().isGone());
    }

    @Test
    void expiryInThePastIsRejected() throws Exception {
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/a\","
                                + " \"expires_at\": \"2000-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void homePageIsServed() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk());
    }
}
