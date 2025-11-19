package org.workfitai.apigateway.test;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.workfitai.apigateway.service.IOpaqueTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Test runner to validate opaque token functionality
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpaqueTokenTestRunner implements CommandLineRunner {

    private final IOpaqueTokenService opaqueTokenService;

    @Override
    public void run(String... args) throws Exception {
        log.info("🧪 [TEST] Starting opaque token test...");

        // Test JWT - có thể replace bằng JWT thật từ auth service
        String sampleJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImV4cCI6OTk5OTk5OTk5OX0.Tr8H8_0_5V2QFk6b_jJhcmP8FaVhTx0QJ3TZnJ5QVDk";

        try {
            // Test mint
            String opaque = opaqueTokenService.mint(sampleJwt, "access").block();
            log.info("🧪 [TEST] Minted opaque: {}", opaque);

            // Test lookup
            String retrievedJwt = opaqueTokenService.toJwt(opaque).block();
            log.info("🧪 [TEST] Retrieved JWT: {}", retrievedJwt != null ? "SUCCESS" : "FAILED");

            if (sampleJwt.equals(retrievedJwt)) {
                log.info("🧪 [TEST] ✅ Opaque token service working correctly!");
            } else {
                log.error("🧪 [TEST] ❌ JWT mismatch! Expected: {}, Got: {}", sampleJwt, retrievedJwt);
            }

        } catch (Exception e) {
            log.error("🧪 [TEST] ❌ Test failed: {}", e.getMessage());
        }
    }
}