package br.ufrn.imd.imdtravel.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationAndLoggingFilter extends OncePerRequestFilter {

    private final JsonLogger jsonLogger;

    public CorrelationAndLoggingFilter(JsonLogger jsonLogger) {
        this.jsonLogger = jsonLogger;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String cid = CorrelationContext.resolveOrGenerate(
                request.getHeader(CorrelationContext.HEADER_NAME));
        CorrelationContext.setCid(cid);
        request.setAttribute(CorrelationContext.MDC_KEY, cid);
        response.setHeader(CorrelationContext.HEADER_NAME, cid);

        String path = request.getRequestURI();
        String method = request.getMethod();
        boolean isHealth = "/health".equals(path);

        long startNanos = System.nanoTime();
        if (!isHealth) {
            jsonLogger.logRequestReceived(cid, method, path);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;
            int status = response.getStatus();
            if (!isHealth || status != HttpServletResponse.SC_OK) {
                jsonLogger.logResponseSent(cid, method, path, status, elapsedMs);
            }
            CorrelationContext.clear();
        }
    }
}
