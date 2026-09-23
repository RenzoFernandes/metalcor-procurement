package com.metalcor.procurement.common;

/** Request content is well-formed but fails a business rule (e.g. references a row that does not exist). Maps to 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}