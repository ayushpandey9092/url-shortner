package com.example.url_shortner.service;

/**
 * Thrown when someone asks for an alias that another link already uses.
 */
public class AliasTakenException extends RuntimeException {

    public AliasTakenException(String alias) {
        super("The alias '" + alias + "' is already taken.");
    }
}
