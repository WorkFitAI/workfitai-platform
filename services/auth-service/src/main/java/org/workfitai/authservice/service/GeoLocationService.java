package org.workfitai.authservice.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.workfitai.authservice.model.UserSession;

import java.io.IOException;
import java.net.InetAddress;

@Slf4j
@Service
public class GeoLocationService {

    private DatabaseReader databaseReader;

    public GeoLocationService() {
        // In production, initialize with actual GeoIP2 database
        // For now, we'll return null and handle gracefully
        log.warn("GeoIP2 database not initialized. Location detection disabled.");
    }

    public UserSession.Location getLocation(String ipAddress) {
        if (databaseReader == null || ipAddress == null) {
            return createDefaultLocation();
        }

        try {
            InetAddress inetAddress = InetAddress.getByName(ipAddress);
            CityResponse response = databaseReader.city(inetAddress);

            return UserSession.Location.builder()
                    .country(response.getCountry().getName())
                    .city(response.getCity().getName())
                    .region(response.getMostSpecificSubdivision().getName())
                    .latitude(response.getLocation().getLatitude())
                    .longitude(response.getLocation().getLongitude())
                    .build();

        } catch (IOException | GeoIp2Exception e) {
            log.warn("Failed to get location for IP: {}", ipAddress, e);
            return createDefaultLocation();
        }
    }

    private UserSession.Location createDefaultLocation() {
        return UserSession.Location.builder()
                .country("Unknown")
                .city("Unknown")
                .region("Unknown")
                .latitude(0.0)
                .longitude(0.0)
                .build();
    }
}
