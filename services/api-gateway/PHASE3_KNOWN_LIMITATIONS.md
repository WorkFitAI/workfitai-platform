# Phase 3: Known Limitations

## Cache Filter Status

⚠️ **Response body caching is currently DISABLED** due to reactive stream complexity.

### Current Implementation
- Cache filter adds `X-Cache-Status: MISS` header to all cacheable GET requests
- Cache lookups work (would return HIT if data exists in Redis)
- **Response body capture is NOT implemented** (too complex for initial version)

### Why Body Caching Is Disabled
Spring Cloud Gateway uses reactive Netty - capturing response bodies requires:
1. Custom `ServerHttpResponseDecorator`
2. Buffering entire response stream
3. Proper backpressure handling
4. Risk of memory leaks with large responses

### Alternative Approaches
**Option 1: Use Redis Cache at Backend Services** (Recommended)
```java
// In job-service, cv-service, etc.
@Cacheable(value = "jobs", key = "#id")
public JobDTO getJob(String id) { ... }
```

**Option 2: Use CDN/Nginx Caching** (Production)
```nginx
proxy_cache_path /var/cache/nginx levels=1:2 keys_zone=api_cache:10m;
location /job/public {
    proxy_cache api_cache;
    proxy_cache_valid 200 5m;
}
```

**Option 3: Implement with ModifyResponseBodyGatewayFilterFactory**
```java
// Complex implementation - deferred to Phase 4
@Bean
public RouteLocator cacheRoutes(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("cache_jobs", r -> r.path("/job/public/**")
            .filters(f -> f.modifyResponseBody(...))
            .uri("lb://job-service"))
        .build();
}
```

## Compression Status

⚠️ **Server-side compression is NOT available** in Spring Cloud Gateway reactive mode.

### Why Compression Doesn't Work
- `server.compression.enabled` only works with Tomcat/Jetty (blocking)
- Netty (reactive) doesn't support automatic compression
- Spring Cloud Gateway runs on Netty

### Solutions
**Use infrastructure-level compression:**
```nginx
# Nginx (recommended)
gzip on;
gzip_types application/json text/plain;
gzip_min_length 1024;
```

## Test Results
- ✅ **10/12 tests passing**
- ✅ Request validation working (415, 401 errors)
- ✅ Cache headers added (X-Cache-Status)
- ❌ Cache storage disabled (see above)
- ❌ Compression N/A (use nginx)

## Production Recommendations
1. **Caching**: Implement at backend service level with Spring Cache + Redis
2. **Compression**: Configure at nginx/load balancer level
3. **Validation**: Current implementation is production-ready ✅
4. **Monitoring**: Grafana dashboards track validation metrics ✅
