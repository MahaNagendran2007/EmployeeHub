package com.employeehub.exception;

public class InvalidIdException extends RuntimeException {
    public InvalidIdException(String id) {
        super("'" + id + "' is not a valid employee id");
    }
}
