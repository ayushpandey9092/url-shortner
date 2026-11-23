package com.example.url_shortner.web;

/**
 * The JSON body sent with every error: {"detail": "what went wrong"}.
 * The web page reads the detail field to show the message.
 */
public record ErrorBody(String detail) {
}
