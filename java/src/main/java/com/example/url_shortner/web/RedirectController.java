package com.example.url_shortner.web;

import com.example.url_shortner.service.LinkService;
import com.example.url_shortner.storage.ShortUrl;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles visits to a short link: sends the visitor on to the original URL.
 */
@RestController
public class RedirectController {

    private final LinkService linkService;

    public RedirectController(LinkService linkService) {
        this.linkService = linkService;
    }

    // GET /{code}: for example GET /W9eTrsF.
    // The pattern allows only letters, digits, '-' and '_', so a file name such as
    // /index.html (which has a dot) is left for the static web page to handle.
    @GetMapping("/{code:[A-Za-z0-9_-]+}")
    public ResponseEntity<Void> redirect(
            @PathVariable String code,
            @RequestHeader(name = "Referer", required = false) String referrer,
            @RequestHeader(name = "User-Agent", required = false) String userAgent) {

        // Step 1: find the link. Unknown or deleted links give 404.
        Optional<ShortUrl> found = linkService.findForVisit(code);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ShortUrl link = found.get();

        // Step 2: a link past its expiry time gives 410 Gone.
        if (linkService.isExpired(link)) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        // Step 3: count this visit.
        linkService.recordVisit(link, referrer, userAgent);

        // Step 4: send the browser to the original URL with 302 Found.
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.originalUrl()))
                .build();
    }
}
