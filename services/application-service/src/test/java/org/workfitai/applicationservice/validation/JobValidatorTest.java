package org.workfitai.applicationservice.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.port.outbound.JobServicePort;

@ExtendWith(MockitoExtension.class)
class JobValidatorTest {

    @Mock JobServicePort jobServicePort;

    @InjectMocks JobValidator validator;

    private CreateApplicationRequest req(String jobId) {
        return CreateApplicationRequest.builder()
                .jobId(jobId).email("a@b.com").build();
    }

    // ─── job exists ───────────────────────────────────────────────────────────

    @Test
    void validate_jobExists_passes() {
        when(jobServicePort.jobExists("job-1")).thenReturn(true);

        assertThatCode(() -> validator.validate(req("job-1"), "user1"))
                .doesNotThrowAnyException();
    }

    // ─── job not found / not published ────────────────────────────────────────

    @Test
    void validate_jobNotFound_throwsNotFound() {
        when(jobServicePort.jobExists("bad-job")).thenReturn(false);

        assertThatThrownBy(() -> validator.validate(req("bad-job"), "user1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("bad-job");
    }

    // ─── getOrder ─────────────────────────────────────────────────────────────

    @Test
    void getOrder_returnsThree() {
        assertThat(validator.getOrder()).isEqualTo(3);
    }
}
