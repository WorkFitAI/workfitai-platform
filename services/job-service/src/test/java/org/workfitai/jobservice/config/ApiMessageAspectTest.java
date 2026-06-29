package org.workfitai.jobservice.config;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.util.ApiMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiMessageAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private ApiMessage apiMessage;

    private final ApiMessageAspect aspect = new ApiMessageAspect();

    @Test
    void handleApiMessage_overridesMessage_whenBodyIsResponseEntityWrappingRestResponse() throws Throwable {
        when(apiMessage.value()).thenReturn("Job created successfully");
        RestResponse<String> original = new RestResponse<>(200, "Success", "data");
        when(joinPoint.proceed()).thenReturn(ResponseEntity.status(HttpStatus.CREATED).body(original));

        Object result = aspect.handleApiMessage(joinPoint, apiMessage);

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        RestResponse<?> body = (RestResponse<?>) response.getBody();
        assertThat(body.getMessage()).isEqualTo("Job created successfully");
        assertThat(body.getData()).isEqualTo("data");
    }

    @Test
    void handleApiMessage_overridesMessage_whenBodyIsPlainRestResponse() throws Throwable {
        when(apiMessage.value()).thenReturn("Updated successfully");
        RestResponse<String> original = new RestResponse<>(200, "Success", "data");
        when(joinPoint.proceed()).thenReturn(original);

        Object result = aspect.handleApiMessage(joinPoint, apiMessage);

        RestResponse<?> body = (RestResponse<?>) result;
        assertThat(body.getMessage()).isEqualTo("Updated successfully");
    }

    @Test
    void handleApiMessage_returnsResultUnchanged_whenNotARestResponse() throws Throwable {
        when(joinPoint.proceed()).thenReturn("plain string result");

        Object result = aspect.handleApiMessage(joinPoint, apiMessage);

        assertThat(result).isEqualTo("plain string result");
    }

    @Test
    void handleApiMessage_returnsResponseEntityUnchanged_whenBodyIsNotRestResponse() throws Throwable {
        when(joinPoint.proceed()).thenReturn(ResponseEntity.ok("plain body"));

        Object result = aspect.handleApiMessage(joinPoint, apiMessage);

        ResponseEntity<?> response = (ResponseEntity<?>) result;
        assertThat(response.getBody()).isEqualTo("plain body");
    }
}
