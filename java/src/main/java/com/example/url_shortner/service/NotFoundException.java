package com.example.url_shortner.service;

/**
 * Thrown when a link does not exist or was deleted. The web layer turns it into 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
