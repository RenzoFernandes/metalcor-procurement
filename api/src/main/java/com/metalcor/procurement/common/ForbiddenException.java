package com.metalcor.procurement.common;

/** The acting user does not hold the role required for the action. Maps to 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}