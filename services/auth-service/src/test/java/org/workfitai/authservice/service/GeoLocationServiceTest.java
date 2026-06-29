package org.workfitai.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.workfitai.authservice.model.UserSession;

@ExtendWith(MockitoExtension.class)
class GeoLocationServiceTest {

    @Mock RestTemplate restTemplate;

    private GeoLocationService service;

    @BeforeEach
    void setUp() {
        service = new GeoLocationService();
        ReflectionTestUtils.setField(service, "restTemplate", restTemplate);
    }

    // ─── getLocation: null / empty / local IPs ────────────────────────────────

    @Test
    void getLocation_nullIp_returnsDefaultLocation() {
        UserSession.Location loc = service.getLocation(null);
        assertDefaultLocation(loc);
    }

    @Test
    void getLocation_emptyIp_returnsDefaultLocation() {
        UserSession.Location loc = service.getLocation("");
        assertDefaultLocation(loc);
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "127.1.2.3", "192.168.0.1", "192.168.100.50",
            "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255",
            "0:0:0:0:0:0:0:1", "::1"})
    void getLocation_localOrLoopbackIp_returnsDefaultLocation(String ip) {
        UserSession.Location loc = service.getLocation(ip);
        assertDefaultLocation(loc);
    }

    // ─── getLocation: public IP success path ──────────────────────────────────

    @Test
    void getLocation_publicIp_successResponse_returnsLocation() {
        String json = """
                {"status":"success","country":"Vietnam","city":"Ho Chi Minh City",
                "regionName":"Ho Chi Minh","lat":10.8231,"lon":106.6297}
                """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        UserSession.Location loc = service.getLocation("203.162.0.1");

        assertThat(loc.getCountry()).isEqualTo("Vietnam");
        assertThat(loc.getCity()).isEqualTo("Ho Chi Minh City");
        assertThat(loc.getRegion()).isEqualTo("Ho Chi Minh");
        assertThat(loc.getLatitude()).isEqualTo(10.8231);
        assertThat(loc.getLongitude()).isEqualTo(106.6297);
    }

    @Test
    void getLocation_publicIp_failStatus_returnsDefaultLocation() {
        String json = "{\"status\":\"fail\",\"message\":\"private range\"}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        UserSession.Location loc = service.getLocation("203.162.0.1");

        assertDefaultLocation(loc);
    }

    @Test
    void getLocation_publicIp_restTemplateThrows_returnsDefaultLocation() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        UserSession.Location loc = service.getLocation("203.162.0.1");

        assertDefaultLocation(loc);
    }

    @Test
    void getLocation_publicIp_malformedJson_returnsDefaultLocation() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("not-json");

        UserSession.Location loc = service.getLocation("203.162.0.1");

        assertDefaultLocation(loc);
    }

    // ─── getLocationFromCoordinates ───────────────────────────────────────────

    @Test
    void getLocationFromCoordinates_nullLatitude_returnsDefaultLocation() {
        UserSession.Location loc = service.getLocationFromCoordinates(null, 106.6297);
        assertDefaultLocation(loc);
    }

    @Test
    void getLocationFromCoordinates_nullLongitude_returnsDefaultLocation() {
        UserSession.Location loc = service.getLocationFromCoordinates(10.8231, null);
        assertDefaultLocation(loc);
    }

    @Test
    void getLocationFromCoordinates_bothNull_returnsDefaultLocation() {
        UserSession.Location loc = service.getLocationFromCoordinates(null, null);
        assertDefaultLocation(loc);
    }

    @Test
    void getLocationFromCoordinates_success_withCity_returnsLocation() {
        String json = """
                {"address":{"city":"Ho Chi Minh City","state":"Ho Chi Minh","country":"Vietnam"}}
                """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        UserSession.Location loc = service.getLocationFromCoordinates(10.8231, 106.6297);

        assertThat(loc.getCity()).isEqualTo("Ho Chi Minh City");
        assertThat(loc.getRegion()).isEqualTo("Ho Chi Minh");
        assertThat(loc.getCountry()).isEqualTo("Vietnam");
        assertThat(loc.getLatitude()).isEqualTo(10.8231);
        assertThat(loc.getLongitude()).isEqualTo(106.6297);
    }

    @Test
    void getLocationFromCoordinates_success_withTownFallback_returnsLocation() {
        String json = """
                {"address":{"town":"Small Town","state":"Region","country":"Country"}}
                """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        UserSession.Location loc = service.getLocationFromCoordinates(1.0, 2.0);

        assertThat(loc.getCity()).isEqualTo("Small Town");
    }

    @Test
    void getLocationFromCoordinates_success_withVillageFallback_returnsLocation() {
        String json = """
                {"address":{"village":"Tiny Village","region":"RegionX","country":"CountryY"}}
                """;
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        UserSession.Location loc = service.getLocationFromCoordinates(1.0, 2.0);

        assertThat(loc.getCity()).isEqualTo("Tiny Village");
    }

    @Test
    void getLocationFromCoordinates_noAddressNode_returnsDefaultLocation() {
        String json = "{\"error\":\"Unable to geocode\"}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(json);

        UserSession.Location loc = service.getLocationFromCoordinates(10.8231, 106.6297);

        assertDefaultLocation(loc);
    }

    @Test
    void getLocationFromCoordinates_restTemplateThrows_returnsDefaultLocation() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Timeout"));

        UserSession.Location loc = service.getLocationFromCoordinates(10.8231, 106.6297);

        assertDefaultLocation(loc);
    }

    // ─── helper ───────────────────────────────────────────────────────────────

    private void assertDefaultLocation(UserSession.Location loc) {
        assertThat(loc).isNotNull();
        assertThat(loc.getCountry()).isEqualTo("Unknown");
        assertThat(loc.getCity()).isEqualTo("Unknown");
        assertThat(loc.getRegion()).isEqualTo("Unknown");
        assertThat(loc.getLatitude()).isEqualTo(0.0);
        assertThat(loc.getLongitude()).isEqualTo(0.0);
    }
}
