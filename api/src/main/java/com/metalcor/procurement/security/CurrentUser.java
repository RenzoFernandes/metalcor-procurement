package com.metalcor.procurement.security;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Provisional auth: holds the app_users id and role resolved from the X-User-Id header for the
 * current request, set once by CurrentUserInterceptor. Request-scoped, so each request gets its own
 * instance behind a proxy injected into singleton beans.
 */
@Component
@RequestScope
public class CurrentUser {

    private Long id;
    private String role;

    public void assign(long id, String role) {
        this.id = id;
        this.role = role;
    }

    public long id() {
        requireAssigned();
        return id;
    }

    public String role() {
        requireAssigned();
        return role;
    }

    private void requireAssigned() {
        if (id == null) {
            throw new IllegalStateException("CurrentUser was not resolved for this request.");
        }
    }
}