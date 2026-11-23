package com.example.url_shortner.web;

import com.example.url_shortner.service.LinkService;
import com.example.url_shortner.service.StatsResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles statistics and deletion for an existing link.
 */
@RestController
public class LinkController {

    private final LinkService linkService;

    public LinkController(LinkService linkService) {
        this.linkService = linkService;
    }

    // GET /api/urls/{code}/stats: totals and recent activity for one link.
    @GetMapping("/api/urls/{code}/stats")
    public StatsResult stats(@PathVariable String code) {
        return linkService.stats(code);
    }

    // DELETE /api/urls/{code}: needs the delete token in the X-Delete-Token header.
    // 204 No Content means the link was deleted and there is nothing to send back.
    @DeleteMapping("/api/urls/{code}")
    public ResponseEntity<Void> delete(
            @PathVariable String code,
            @RequestHeader(name = "X-Delete-Token", required = false) String deleteToken) {
        linkService.delete(code, deleteToken);
        return ResponseEntity.noContent().build();
    }
}
