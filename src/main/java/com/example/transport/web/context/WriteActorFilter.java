package com.example.transport.web.context;

import com.example.transport.application.context.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
public class WriteActorFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (!requiresActor(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        var context = (RequestContext) request.getAttribute(
                RequestContextFilter.REQUEST_CONTEXT_ATTRIBUTE
        );

        if (context == null || !context.hasActor()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");

            response.getWriter().write("""
                {
                  "code": "MISSING_ACTOR",
                  "message": "X-User-Id is required for write operations"
                }
                """);

            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresActor(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith("/api/")) {
            return false;
        }

        return switch (request.getMethod()) {
            case "POST", "PUT", "PATCH", "DELETE" -> true;
            default -> false;
        };
    }
}