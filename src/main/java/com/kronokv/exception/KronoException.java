package com.kronokv.exception;

public class KronoException extends RuntimeException {
    public KronoException(String message) {
        super(message);
    }

    public KronoException(String message, Throwable cause) {
        super(message, cause);
    }
}
