package org.example.springproject.exceptions;

public class DeadlockException extends RuntimeException {
    public DeadlockException(String message) {
        super(message);
    }

    public DeadlockException(String message, Throwable cause) {
        super(message, cause);
    }
}

