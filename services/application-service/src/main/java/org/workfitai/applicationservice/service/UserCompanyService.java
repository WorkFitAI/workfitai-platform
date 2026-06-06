package org.workfitai.applicationservice.service;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.client.UserServiceClient;
import org.workfitai.applicationservice.exception.BadRequestException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for fetching and caching user company information.
 *
 * Cache: Caffeine with 10-minute TTL and 5000-entry cap (M5 fix).
 * Entries are evicted automatically — stale data from HR transfers clears within 10 min.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserCompanyService {

    private static final int CACHE_MAX_SIZE = 5000;
    private static final int CACHE_TTL_MINUTES = 10;

    private final UserServiceClient userServiceClient;

    private final Cache<String, String> companyIdCache = Caffeine.newBuilder()
            .maximumSize(CACHE_MAX_SIZE)
            .expireAfterWrite(CACHE_TTL_MINUTES, TimeUnit.MINUTES)
            .build();

    /**
     * Get company ID for a user by username.
     *
     * @param username Username to lookup
     * @return Company ID, or null if user has no company (CANDIDATE/ADMIN)
     * @throws BadRequestException if user not found or service error
     */
    public String getUserCompanyId(String username) {
        // Caffeine returns null on miss; sentinel "" represents a cached "no company" result
        String cached = companyIdCache.getIfPresent(username);
        if (cached != null) {
            log.debug("Cache HIT: companyId for username={}", username);
            return cached.isEmpty() ? null : cached;
        }

        log.debug("Cache MISS: fetching companyId for username={} from user-service", username);

        try {
            UserServiceClient.UserInfo userInfo = userServiceClient.getUserByUsername(username).getData();

            if (userInfo == null) {
                log.error("User-service returned null data for username={}", username);
                throw new BadRequestException("User not found: " + username);
            }

            String companyId = userInfo.companyId();

            if (companyId == null) {
                log.warn("User {} has no companyId (CANDIDATE or ADMIN)", username);
                companyIdCache.put(username, ""); // cache sentinel to avoid repeated lookups
                return null;
            }

            companyIdCache.put(username, companyId);
            log.info("Cached companyId={} for username={}", companyId, username);
            return companyId;

        } catch (FeignException.NotFound e) {
            log.error("User not found in user-service: username={}", username);
            throw new BadRequestException("User not found: " + username);

        } catch (FeignException e) {
            log.error("Failed to fetch user from user-service: username={}, status={}", username, e.status());
            throw new BadRequestException("Failed to fetch user information. Please try again later.");

        } catch (BadRequestException e) {
            throw e;

        } catch (Exception e) {
            log.error("Unexpected error fetching user company: username={}", username, e);
            throw new BadRequestException("Failed to validate user company. Please try again later.");
        }
    }

    /**
     * Evict a specific user from the cache (e.g., after a company transfer).
     */
    public void evictCache(String username) {
        companyIdCache.invalidate(username);
        log.info("Evicted cache for username={}", username);
    }

    /**
     * Clear entire cache.
     */
    public void clearCache() {
        companyIdCache.invalidateAll();
        log.info("Cleared entire company ID cache");
    }

    public long getCacheSize() {
        return companyIdCache.estimatedSize();
    }
}
