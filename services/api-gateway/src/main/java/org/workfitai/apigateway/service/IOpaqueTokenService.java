package org.workfitai.apigateway.service;

import reactor.core.publisher.Mono;

public interface IOpaqueTokenService {
  Mono<String> mint(String jwt, String kind);             // kind: "access" | "refresh"

  Mono<String> toJwt(String opaque);                      // lookup bất kỳ (access/refresh)

  Mono<Long> revokeAll(String opaque);
}
