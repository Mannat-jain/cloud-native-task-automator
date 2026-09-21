package com.example.task.exception;

/** The requested operation is not allowed in the task's current state (mapped to HTTP 409). */
public class InvalidTaskStateException extends RuntimeException {
    public InvalidTaskStateException(String message) {
        super(message);
    }
}
