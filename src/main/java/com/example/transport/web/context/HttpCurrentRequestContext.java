package com.example.transport.web.context;

import com.example.transport.application.context.CurrentRequestContext;
import com.example.transport.application.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class HttpCurrentRequestContext implements CurrentRequestContext {

    private final RequestContext context;

    public HttpCurrentRequestContext(HttpServletRequest request) {
        var attribute = request.getAttribute(
                RequestContextFilter.REQUEST_CONTEXT_ATTRIBUTE
        );

        if (!(attribute instanceof RequestContext requestContext)) {
            throw new IllegalStateException(
                    "RequestContext has not been initialized"
            );
        }

        this.context = requestContext;
    }

    @Override
    public RequestContext get() {
        return context;
    }
}