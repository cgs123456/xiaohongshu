package com.itcast.exception;

public class NoteNoExistException extends RuntimeException {

    public NoteNoExistException(String message) {
        super(message);
    }
}
