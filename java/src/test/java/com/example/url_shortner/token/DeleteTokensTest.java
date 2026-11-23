package com.example.url_shortner.token;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class DeleteTokensTest {

    @Test
    void hashIsSixtyFourCharacters() {
        // SHA-256 always gives 32 bytes, which is 64 hex characters.
        assertEquals(64, DeleteTokens.hash("my-token").length());
    }

    @Test
    void hashOfAbcMatchesTheKnownValue() {
        // "abc" is a standard test input for SHA-256, so the answer is well known.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                DeleteTokens.hash("abc"));
    }

    @Test
    void sameTokenGivesSameHash() {
        assertEquals(DeleteTokens.hash("abc"), DeleteTokens.hash("abc"));
    }

    @Test
    void differentTokensGiveDifferentHashes() {
        assertNotEquals(DeleteTokens.hash("abc"), DeleteTokens.hash("abd"));
    }

    @Test
    void newTokenIsDifferentEachTime() {
        assertNotEquals(DeleteTokens.newToken(), DeleteTokens.newToken());
    }
}
