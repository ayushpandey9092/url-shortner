package com.example.url_shortner.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Checks a destination URL before it is shortened.
 *
 * <p>Java port of {@code normalize_and_validate_url} in {@code app/validators.py}.
 */
public final class UrlValidator {

    // One DNS label: letters, digits and hyphens, no leading or trailing hyphen.
    private static final Pattern LABEL = Pattern.compile("^(?!-)[a-z0-9-]{1,63}(?<!-)$");
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private UrlValidator() {
    }

    /**
     * Returns the cleaned-up URL (with https:// added if it had no scheme).
     *
     * @throws IllegalArgumentException with a message the user can read, if the URL is rejected
     */
    public static String normalizeAndValidate(String raw, int maxLength, String ownHost) {
        String url = raw.strip();
        if (url.isEmpty()) {
            throw new IllegalArgumentException("URL must not be empty.");
        }
        if (url.length() > maxLength) {
            throw new IllegalArgumentException("URL is longer than " + maxLength + " characters.");
        }
        if (url.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("URL must not contain whitespace.");
        }

        if (!url.contains("://")) {
            // "javascript:..." or "mailto:..." have a scheme but no "//". Reject those,
            // but allow "example.com:8080/page" (a host with a port).
            int colon = url.indexOf(':');
            if (colon >= 0) {
                String scheme = url.substring(0, colon);
                String rest = url.substring(colon + 1);
                boolean startsWithDigit = !rest.isEmpty() && Character.isDigit(rest.charAt(0));
                if (!scheme.contains(".") && !startsWithDigit) {
                    throw new IllegalArgumentException("Only http and https URLs can be shortened.");
                }
            }
            // Accept "example.com/page" by assuming https, as most users expect.
            url = "https://" + url;
        }

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("URL is not well formed.", e);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only http and https URLs can be shortened.");
        }

        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) {
            throw new IllegalArgumentException("URL must include a host name.");
        }
        if (!isPlausibleHost(host)) {
            throw new IllegalArgumentException("URL host name is not valid.");
        }
        if (ownHost != null && host.equals(ownHost.toLowerCase(Locale.ROOT))) {
            // A short link pointing at another short link can create redirect loops.
            throw new IllegalArgumentException(
                    "URLs on this shortener's own domain cannot be shortened.");
        }
        int port = uri.getPort();
        if (port != -1 && (port <= 0 || port >= 65536)) {
            throw new IllegalArgumentException("URL port is out of range.");
        }

        return url;
    }

    private static boolean isPlausibleHost(String host) {
        // IP addresses are accepted as they are.
        if (IPV4.matcher(host).matches() || host.contains(":")) {
            return true;
        }
        if (host.equals("localhost") || !host.contains(".")) {
            return false;
        }
        // Each dot-separated label must be valid. Punycode labels (xn--) are allowed.
        for (String label : host.split("\\.", -1)) {
            if (!LABEL.matcher(label).matches() && !label.startsWith("xn--")) {
                return false;
            }
        }
        return true;
    }
}
