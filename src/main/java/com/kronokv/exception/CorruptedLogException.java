package com.kronokv.exception;

public class CorruptedLogException extends KronoException {
    public CorruptedLogException(String message) {
        super(message);
    }

    public CorruptedLogException(String message, Throwable cause) {
        super(message, cause);
    }
}
