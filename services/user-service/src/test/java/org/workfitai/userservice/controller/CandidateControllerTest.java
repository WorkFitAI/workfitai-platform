package org.workfitai.userservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.workfitai.userservice.dto.request.CandidateCreateRequest;
import org.workfitai.userservice.dto.request.CandidateUpdateRequest;
import org.workfitai.userservice.dto.response.CandidateResponse;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.service.CandidateService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CandidateControllerTest {

    @Mock CandidateService candidateService;

    @InjectMocks
    CandidateController controller;

    private CandidateResponse response(UUID id) {
        CandidateResponse r = new CandidateResponse();
        r.setUserId(id);
        r.setFullName("Test Candidate");
        return r;
    }

    @Test
    void create_returnsCreatedCandidate() {
        UUID id = UUID.randomUUID();
        CandidateCreateRequest req = new CandidateCreateRequest();
        when(candidateService.create(req)).thenReturn(response(id));

        ResponseEntity<ResponseData<CandidateResponse>> resp = controller.create(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getUserId()).isEqualTo(id);
        verify(candidateService).create(req);
    }

    @Test
    void update_returnsUpdatedCandidate() {
        UUID id = UUID.randomUUID();
        CandidateUpdateRequest req = new CandidateUpdateRequest();
        when(candidateService.update(id, req)).thenReturn(response(id));

        ResponseEntity<ResponseData<CandidateResponse>> resp = controller.update(id, req);

        assertThat(resp.getBody().getData().getUserId()).isEqualTo(id);
        verify(candidateService).update(id, req);
    }

    @Test
    void delete_callsServiceAndReturnsOk() {
        UUID id = UUID.randomUUID();
        doNothing().when(candidateService).delete(id);

        ResponseEntity<ResponseData<Void>> resp = controller.delete(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(candidateService).delete(id);
    }

    @Test
    void getById_returnsCandidate() {
        UUID id = UUID.randomUUID();
        when(candidateService.getById(id)).thenReturn(response(id));

        ResponseEntity<ResponseData<CandidateResponse>> resp = controller.getById(id);

        assertThat(resp.getBody().getData().getUserId()).isEqualTo(id);
    }

    @Test
    void search_returnsPage() {
        UUID id = UUID.randomUUID();
        Page<CandidateResponse> page = new PageImpl<>(List.of(response(id)));
        when(candidateService.search("java", Pageable.unpaged())).thenReturn(page);

        ResponseEntity<ResponseData<Page<CandidateResponse>>> resp =
                controller.search("java", Pageable.unpaged());

        assertThat(resp.getBody().getData().getTotalElements()).isEqualTo(1);
    }

    @Test
    void getExperienceStats_delegatesToService() {
        when(candidateService.getExperienceStats()).thenReturn(
                java.util.Map.of("Junior", 10L, "Senior", 5L));

        ResponseEntity<ResponseData<Object>> resp = controller.getExperienceStats();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(candidateService).getExperienceStats();
    }
}
