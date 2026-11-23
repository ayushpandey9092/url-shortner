package com.example.url_shortner.validation;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks a custom alias (a short code the user picks) before it is used.
 *
 * <p>Java port of {@code validate_alias} in {@code app/validators.py}.
 */
public final class AliasValidator {

    // 3 to 30 characters: letters, digits, '-' or '_'. Other symbols are not allowed.
    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9_-]{3,30}$");

    // Words the app uses for its own pages. An alias must never clash with them.
    private static final List<String> RESERVED = List.of(
            "api", "docs", "redoc", "openapi.json", "health", "static", "admin",
            "login", "logout", "signup", "register", "favicon.ico", "robots.txt",
            "index", "about", "help", "stats");

    private AliasValidator() {
    }

    /**
     * Returns the cleaned-up alias, or throws IllegalArgumentException with the reason.
     */
    public static String validate(String alias) {
        String cleaned = alias.strip();

        if (!ALLOWED.matcher(cleaned).matches()) {
            throw new IllegalArgumentException(
                    "Custom alias must be 3-30 characters using letters, digits, '-' or '_'.");
        }
        if (RESERVED.contains(cleaned.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("'" + cleaned + "' is reserved. Choose another alias.");
        }
        if (looksGenerated(cleaned)) {
            // Generated codes are exactly 7 letters and digits. An alias of that shape
            // could clash with a future generated code, so it is not allowed.
            throw new IllegalArgumentException(
                    "Custom aliases cannot be exactly 7 letters and digits. Add a '-' or '_'.");
        }
        return cleaned;
    }

    private static boolean looksGenerated(String alias) {
        return alias.length() == 7 && alias.chars().allMatch(Character::isLetterOrDigit);
    }
}
