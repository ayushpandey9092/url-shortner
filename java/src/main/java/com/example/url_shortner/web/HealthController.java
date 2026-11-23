package com.example.url_shortner.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A simple "are you alive?" check. Docker and CI use it to see if the app has started.
 */
@RestController
public class HealthController {

    // GET /health returns {"status": "ok"} when the app is running.
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
