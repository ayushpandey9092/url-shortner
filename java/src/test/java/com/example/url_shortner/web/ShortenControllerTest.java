package com.example.url_shortner.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc // lets the test send fake HTTP requests without starting a server
@Transactional // undo the database changes after each test
class ShortenControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void postShortenReturns201WithAShortUrlAndADeleteToken() throws Exception {
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"https://example.com/some/long/path\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.short_url").isString())
                .andExpect(jsonPath("$.delete_token").isString());
    }

    @Test
    void postShortenWithABadUrlReturns400WithAReason() throws Exception {
        mockMvc.perform(post("/api/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\": \"javascript:alert(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").isString());
    }
}
