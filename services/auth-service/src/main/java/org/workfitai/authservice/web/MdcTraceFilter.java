package org.workfitai.authservice.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class MdcTraceFilter extends OncePerRequestFilter {
    public static final String TRACE_ID = "traceId";
    public static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String existing = req.getHeader(TRACE_HEADER);
        String traceId = (existing != null && !existing.isBlank()) ? existing : UUID.randomUUID().toString();

        MDC.put(TRACE_ID, traceId);
        res.setHeader(TRACE_HEADER, traceId);

        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}