package com.metalcor.procurement.security;

import com.metalcor.procurement.common.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Reads X-User-Id on every /api/v1/** request. Required on writes (any method other than GET),
 * optional on GET. When present, the id must identify an active app_users row; otherwise 401.
 */
@Component
public class CurrentUserInterceptor implements HandlerInterceptor {

    private static final String HEADER = "X-User-Id";

    private final JdbcClient jdbc;
    private final CurrentUser currentUser;

    public CurrentUserInterceptor(JdbcClient jdbc, CurrentUser currentUser) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader(HEADER);
        boolean write = !"GET".equalsIgnoreCase(request.getMethod());

        if (header == null || header.isBlank()) {
            if (write) {
                throw new UnauthorizedException("Header " + HEADER + " is required.");
            }
            return true;
        }

        long userId;
        try {
            userId = Long.parseLong(header.trim());
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Header " + HEADER + " must be a valid user id.");
        }

        Optional<String> role = jdbc.sql("SELECT role FROM app_users WHERE id = :id AND active")
                .param("id", userId)
                .query(String.class)
                .optional();

        if (role.isEmpty()) {
            throw new UnauthorizedException("Header " + HEADER + " does not identify an active user.");
        }

        currentUser.assign(userId, role.get());
        return true;
    }
}