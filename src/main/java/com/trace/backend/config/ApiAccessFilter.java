package com.trace.backend.config;

import com.trace.backend.service.GuestAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiAccessFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS =
            Set.of("GET", "HEAD", "OPTIONS");

    private final GuestAuthService authService;
    private final Set<String> allowedOrigins;

    public ApiAccessFilter(
            GuestAuthService authService,
            @Value(
                    "${app.auth.allowed-origins:"
                            + "http://localhost:8080,http://localhost:5173}"
            )
            String origins
    ) {
        this.authService = authService;
        this.allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getServletPath();

        if (!path.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader("Cache-Control", "no-store");

        if (!SAFE_METHODS.contains(request.getMethod())) {
            if (!"true".equals(request.getHeader("X-Trace-Request"))) {
                response.sendError(
                        403,
                        "X-Trace-Request başlığı gerekli."
                );
                return;
            }

            String origin = request.getHeader("Origin");

            if (origin != null && !allowedOrigins.contains(origin)) {
                response.sendError(403, "İstek kaynağına izin verilmiyor.");
                return;
            }

            if ("cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
                response.sendError(403, "Siteler arası isteğe izin verilmiyor.");
                return;
            }
        }

        if (path.equals("/api/admin") || path.startsWith("/api/admin/")) {
            try {
                authService.requireAdmin(readToken(request));
            } catch (ResponseStatusException exception) {
                response.sendError(
                        exception.getStatusCode().value(),
                        exception.getReason()
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String readToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if ("trace_guest_token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }
}