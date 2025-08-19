package org.workfitai.apigateway.config;

import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;

import java.util.Map;

public class Slf4jMdcAccessor implements ThreadLocalAccessor<Map<String, String>> {
    @Override public String key() { return "mdc"; }
    @Override public Map<String, String> getValue() { return MDC.getCopyOfContextMap(); }
    @Override public void setValue(Map<String, String> value) {
        if (value == null) MDC.clear(); else MDC.setContextMap(value);
    }
    @Override public void reset() { MDC.clear(); }
}