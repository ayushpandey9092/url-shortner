package com.example.url_shortner.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Makes delete tokens.
 *
 * <p>The creator gets the token once. We store only its hash, so if the database
 * leaks, nobody can use the stored values to delete a link.
 */
public class DeleteTokens {

    // Makes a new random token, for example "3f2a9c...". Each call gives a different one.
    public static String newToken() {
        return UUID.randomUUID().toString();
    }

    // Turns a token into its SHA-256 hash, written as 64 hex characters.
    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // Every Java installation includes SHA-256, so this should never happen.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
