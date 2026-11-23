package com.example.url_shortner.service;

/**
 * Thrown when someone tries to delete a link with the wrong (or no) delete token.
 * The web layer turns it into 403 Forbidden.
 */
public class WrongTokenException extends RuntimeException {

    public WrongTokenException() {
        super("Wrong or missing delete token.");
    }
}
