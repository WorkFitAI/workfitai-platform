package org.workfitai.applicationservice.validation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.exception.ApplicationConflictException;

class ValidationPipelineTest {

    private final CreateApplicationRequest request = CreateApplicationRequest.builder()
            .jobId("job-1").email("c@example.com").build();

    private ApplicationValidator mockValidator(int order) {
        ApplicationValidator v = mock(ApplicationValidator.class);
        when(v.getOrder()).thenReturn(order);
        return v;
    }

    @Test
    void validate_allValidatorsPass_eachCalledOnce() {
        ApplicationValidator v1 = mockValidator(1);
        ApplicationValidator v2 = mockValidator(2);
        ApplicationValidator v3 = mockValidator(3);

        ValidationPipeline pipeline = new ValidationPipeline(List.of(v3, v1, v2));
        pipeline.validate(request, "user1");

        verify(v1).validate(request, "user1");
        verify(v2).validate(request, "user1");
        verify(v3).validate(request, "user1");
    }

    @Test
    void validate_validatorsRunInAscendingOrderByGetOrder() {
        ApplicationValidator v1 = mockValidator(1);
        ApplicationValidator v2 = mockValidator(2);

        // Provide in reverse order — pipeline must sort by getOrder()
        ValidationPipeline pipeline = new ValidationPipeline(List.of(v2, v1));
        pipeline.validate(request, "user1");

        InOrder order = inOrder(v1, v2);
        order.verify(v1).validate(any(), eq("user1"));
        order.verify(v2).validate(any(), eq("user1"));
    }

    @Test
    void validate_firstValidatorFails_secondNotCalled() {
        ApplicationValidator v1 = mockValidator(1);
        ApplicationValidator v2 = mockValidator(2);

        doThrow(new ApplicationConflictException("Duplicate"))
                .when(v1).validate(any(), any());

        ValidationPipeline pipeline = new ValidationPipeline(List.of(v1, v2));

        assertThatThrownBy(() -> pipeline.validate(request, "user1"))
                .isInstanceOf(ApplicationConflictException.class);

        verify(v2, never()).validate(any(), any());
    }

    @Test
    void validate_emptyPipeline_passes() {
        ValidationPipeline pipeline = new ValidationPipeline(List.of());
        pipeline.validate(request, "user1"); // must not throw
    }
}
