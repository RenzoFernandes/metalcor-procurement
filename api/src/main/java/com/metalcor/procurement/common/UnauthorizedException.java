package com.metalcor.procurement.common;

/** Missing or invalid X-User-Id header. Maps to 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
