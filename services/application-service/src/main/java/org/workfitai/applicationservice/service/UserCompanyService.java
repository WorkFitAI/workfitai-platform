package org.workfitai.applicationservice.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.client.UserServiceClient;
import org.workfitai.applicationservice.exception.BadRequestException;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for fetching and caching user company information.
 *
 * Provides:
 * - Company ID lookup by username
 * - In-memory caching to reduce user-service calls
 * - Error handling for Feign failures
 *
 * Performance:
 * - Cache hit: O(1) lookup
 * - Cache miss: HTTP call to user-service
 * - Cache eviction: Manual or TTL-based (future enhancement)
 *
 * Phase 4 Implementation:
 * - Basic in-memory cache with ConcurrentHashMap
 * - No TTL expiration (simple implementation)
 * - Manual cache clearing if needed
 *
 * Future Enhancements (Phase 5):
 * - Add Caffeine cache with TTL (10-minute expiration)
 * - Add cache warming on startup
 * - Add cache metrics (hit rate, size)
 * - Consider Redis for distributed caching
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCompanyService {

    private final UserServiceClient userServiceClient;

    // Simple in-memory cache: username -> companyId
    // Thread-safe for concurrent access
    private final ConcurrentMap<String, String> companyIdCache = new ConcurrentHashMap<>();

    /**
     * Get company ID for a user by username.
     *
     * Flow:
     * 1. Check cache first (fast path)
     * 2. If miss, call user-service
     * 3. Store in cache for future requests
     * 4. Return companyId
     *
     * Error Handling:
     * - User not found: throw BadRequestException
     * - Service unavailable: throw BadRequestException with cause
     * - Invalid response: throw BadRequestException
     *
     * @param username Username to lookup
     * @return Company ID (String, not UUID to match Application.companyId type)
     * @throws BadRequestException if user not found or service error
     */
    public String getUserCompanyId(String username) {
        // Check cache first
        String cachedCompanyId = companyIdCache.get(username);
        if (cachedCompanyId != null) {
            log.debug("Cache HIT: Found companyId for username={}", username);
            return cachedCompanyId;
        }

        // Cache miss - fetch from user-service
        log.debug("Cache MISS: Fetching companyId for username={} from user-service", username);

        try {
            UserServiceClient.UserInfo userInfo = userServiceClient.getUserByUsername(username).getData();

            if (userInfo == null) {
                log.error("User-service returned null data for username={}", username);
                throw new BadRequestException("User not found: " + username);
            }

            String companyId = userInfo.companyId();

            if (companyId == null) {
                log.warn("User {} has no companyId (might be CANDIDATE or ADMIN)", username);
                // For CANDIDATE users, companyId is null - this is valid
                // We'll cache null to avoid repeated lookups
                companyIdCache.put(username, ""); // Empty string to represent null
                return null;
            }

            // Cache the result
            companyIdCache.put(username, companyId);
            log.info("Cached companyId={} for username={}", companyId, username);

            return companyId;

        } catch (FeignException.NotFound e) {
            log.error("User not found in user-service: username={}", username);
            throw new BadRequestException("User not found: " + username);

        } catch (FeignException e) {
            log.error("Failed to fetch user from user-service: username={}, status={}, message={}",
                    username, e.status(), e.getMessage());
            throw new BadRequestException("Failed to fetch user information. Please try again later.");

        } catch (Exception e) {
            log.error("Unexpected error fetching user company: username={}", username, e);
            throw new BadRequestException("Failed to validate user company. Please try again later.");
        }
    }

    /**
     * Clear cache for a specific user.
     * Useful when user company changes.
     *
     * @param username Username to evict from cache
     */
    public void evictCache(String username) {
        String removed = companyIdCache.remove(username);
        if (removed != null) {
            log.info("Evicted cache for username={}", username);
        }
    }

    /**
     * Clear entire cache.
     * Useful for testing or manual cache refresh.
     */
    public void clearCache() {
        int size = companyIdCache.size();
        companyIdCache.clear();
        log.info("Cleared entire company ID cache ({} entries)", size);
    }

    /**
     * Get cache statistics for monitoring.
     *
     * @return Current cache size
     */
    public int getCacheSize() {
        return companyIdCache.size();
    }
}
