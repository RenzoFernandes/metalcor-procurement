package com.metalcor.procurement.common;

/** A dependency the request needs is unreachable (e.g. the copilot's model provider). Maps to 503. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}