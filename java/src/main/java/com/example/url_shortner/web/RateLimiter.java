package com.example.url_shortner.web;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits how many links one client (IP address) can create per minute.
 *
 * <p>Simple method: each client gets a one-minute window that starts with their first
 * request. Once the window's count reaches the limit, further requests are refused
 * until the window ends.
 */
@Component
public class RateLimiter {

    private static final long ONE_MINUTE_MILLIS = 60_000;

    private final int limitPerMinute;
    private final Map<String, Window> windows = new HashMap<>();

    public RateLimiter(@Value("${app.rate-limit-per-minute}") int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    /** Returns true if this client may make another request right now. */
    public synchronized boolean allow(String clientAddress) {
        if (limitPerMinute == 0) {
            return true; // the limit is turned off
        }

        long now = System.currentTimeMillis();
        Window window = windows.get(clientAddress);
        if (window == null || now - window.startMillis >= ONE_MINUTE_MILLIS) {
            window = new Window(now);
            windows.put(clientAddress, window);
        }

        if (window.count >= limitPerMinute) {
            return false;
        }
        window.count++;
        return true;
    }

    // One client's counter for the current minute.
    private static class Window {
        private final long startMillis;
        private int count;

        Window(long startMillis) {
            this.startMillis = startMillis;
        }
    }
}
