package com.metalcor.procurement.common;

/** The document exists but is not in a state that allows the requested transition. Maps to 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}