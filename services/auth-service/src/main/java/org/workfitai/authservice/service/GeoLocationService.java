package org.workfitai.authservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.workfitai.authservice.model.UserSession;

@Slf4j
@Service
public class GeoLocationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private static final String IP_API_URL = "http://ip-api.com/json/";

    public GeoLocationService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public UserSession.Location getLocation(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty() || isLocalIpAddress(ipAddress)) {
            log.debug("Local or invalid IP address: {}, returning default location", ipAddress);
            return createDefaultLocation();
        }

        try {
            String url = IP_API_URL + ipAddress;
            String response = restTemplate.getForObject(url, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);

            if ("success".equals(jsonNode.get("status").asText())) {
                return UserSession.Location.builder()
                        .country(jsonNode.get("country").asText())
                        .city(jsonNode.get("city").asText())
                        .region(jsonNode.get("regionName").asText())
                        .latitude(jsonNode.get("lat").asDouble())
                        .longitude(jsonNode.get("lon").asDouble())
                        .build();
            }

            log.warn("IP API returned non-success status for IP: {}", ipAddress);
            return createDefaultLocation();

        } catch (Exception e) {
            log.warn("Failed to get location for IP: {}", ipAddress, e);
            return createDefaultLocation();
        }
    }

    /**
     * Get location from browser-provided coordinates using reverse geocoding
     */
    public UserSession.Location getLocationFromCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return createDefaultLocation();
        }

        try {
            // Using Nominatim reverse geocoding API (OpenStreetMap)
            String url = String.format("https://nominatim.openstreetmap.org/reverse?format=json&lat=%f&lon=%f",
                    latitude, longitude);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode jsonNode = objectMapper.readTree(response);
            JsonNode address = jsonNode.get("address");

            if (address != null) {
                String city = getJsonText(address, "city",
                        getJsonText(address, "town",
                                getJsonText(address, "village", "Unknown")));
                String country = getJsonText(address, "country", "Unknown");
                String region = getJsonText(address, "state",
                        getJsonText(address, "region", "Unknown"));

                return UserSession.Location.builder()
                        .country(country)
                        .city(city)
                        .region(region)
                        .latitude(latitude)
                        .longitude(longitude)
                        .build();
            }

            log.warn("Nominatim API returned no address for coordinates: {}, {}", latitude, longitude);
            return createDefaultLocation();

        } catch (Exception e) {
            log.warn("Failed to get location from coordinates: {}, {}", latitude, longitude, e);
            return createDefaultLocation();
        }
    }

    private String getJsonText(JsonNode node, String field, String defaultValue) {
        JsonNode fieldNode = node.get(field);
        return fieldNode != null && !fieldNode.isNull() ? fieldNode.asText() : defaultValue;
    }

    private boolean isLocalIpAddress(String ipAddress) {
        return ipAddress.startsWith("127.") ||
                ipAddress.startsWith("192.168.") ||
                ipAddress.startsWith("10.") ||
                ipAddress.startsWith("172.") ||
                ipAddress.equals("0:0:0:0:0:0:0:1") ||
                ipAddress.equals("::1");
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
