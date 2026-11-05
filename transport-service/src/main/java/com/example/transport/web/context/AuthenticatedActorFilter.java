package com.example.transport.web.context;

import com.example.transport.application.context.*;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Registered only inside Spring Security, after bearer token authentication.
public class AuthenticatedActorFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.isAuthenticated()) {
            var previous =
                    (RequestContext)
                            request.getAttribute(
                                    RequestContextFilterCustom.REQUEST_CONTEXT_ATTRIBUTE);
            var actor = token.getToken().getSubject();
            var context =
                    new RequestContext(
                            actor, ActorType.USER, previous.requestId(), previous.correlationId());
            request.setAttribute(RequestContextFilterCustom.REQUEST_CONTEXT_ATTRIBUTE, context);
            MDC.put("actorId", actor);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("actorId");
        }
    }
}
