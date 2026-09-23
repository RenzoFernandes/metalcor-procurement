package com.metalcor.procurement.common;

/** The requested document does not exist. Maps to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}