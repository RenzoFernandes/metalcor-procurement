package com.metalcor.procurement.common;

/** A dependency the request needs is unreachable (e.g. the local Ollama copilot). Maps to 503. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}