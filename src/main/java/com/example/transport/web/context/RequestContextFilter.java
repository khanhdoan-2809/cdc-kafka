package com.example.transport.web.context;

import com.example.transport.application.context.ActorType;
import com.example.transport.application.context.RequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestContextFilter extends OncePerRequestFilter {

    public static final String REQUEST_CONTEXT_ATTRIBUTE = RequestContext.class.getName();

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private static final int MAX_ID_LENGTH = 100;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        var actorId = normalize(request.getHeader(USER_ID_HEADER));

        var requestId = resolveId(request.getHeader(REQUEST_ID_HEADER));

        var correlationIdHeader = normalize(request.getHeader(CORRELATION_ID_HEADER));
        var correlationId = correlationIdHeader != null ? correlationIdHeader : requestId;

        var actorType = actorId != null
                ? ActorType.USER
                : ActorType.ANONYMOUS;

        var context = new RequestContext(
                actorId,
                actorType,
                requestId,
                correlationId
        );

        request.setAttribute(REQUEST_CONTEXT_ATTRIBUTE, context);

        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            putMdc(context);

            filterChain.doFilter(request, response);
        } finally {
            removeMdc();
        }
    }

    private String resolveId(String value) {
        var normalized = normalize(value);

        return normalized != null
                ? normalized
                : UUID.randomUUID().toString();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        var normalized = value.trim();

        if (normalized.length() > MAX_ID_LENGTH) {
            return null;
        }

        return normalized;
    }

    private void putMdc(RequestContext context) {
        if (context.actorId() != null) {
            MDC.put("actorId", context.actorId());
        }

        MDC.put("requestId", context.requestId());
        MDC.put("correlationId", context.correlationId());
    }

    private void removeMdc() {
        MDC.remove("actorId");
        MDC.remove("requestId");
        MDC.remove("correlationId");
    }
}