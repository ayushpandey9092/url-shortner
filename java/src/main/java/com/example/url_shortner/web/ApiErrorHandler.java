package com.example.url_shortner.web;

import com.example.url_shortner.service.AliasTakenException;
import com.example.url_shortner.service.NotFoundException;
import com.example.url_shortner.service.WrongTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions from any controller into an HTTP status code and a JSON error body.
 * One place for all the error rules, so each controller stays short.
 */
@RestControllerAdvice
public class ApiErrorHandler {

    // A bad URL, alias or expiry time: 400 Bad Request.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorBody> handleBadInput(IllegalArgumentException error) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorBody(error.getMessage()));
    }

    // The alias is already used: 409 Conflict.
    @ExceptionHandler(AliasTakenException.class)
    public ResponseEntity<ErrorBody> handleTakenAlias(AliasTakenException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorBody(error.getMessage()));
    }

    // The link does not exist or was deleted: 404 Not Found.
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorBody> handleNotFound(NotFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody(error.getMessage()));
    }

    // The delete token is wrong or missing: 403 Forbidden.
    @ExceptionHandler(WrongTokenException.class)
    public ResponseEntity<ErrorBody> handleWrongToken(WrongTokenException error) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorBody(error.getMessage()));
    }
}
