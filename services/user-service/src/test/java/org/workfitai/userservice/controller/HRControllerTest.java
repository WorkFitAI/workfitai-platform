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
import org.workfitai.userservice.dto.request.HRCreateRequest;
import org.workfitai.userservice.dto.request.HRUpdateRequest;
import org.workfitai.userservice.dto.response.HRResponse;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.service.HRService;
import org.workfitai.userservice.service.UserSearchService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HRControllerTest {

    @Mock HRService hrService;
    @Mock UserSearchService userSearchService;

    @InjectMocks
    HRController controller;

    private HRResponse hrResponse(UUID id) {
        HRResponse r = new HRResponse();
        r.setUserId(id);
        r.setFullName("HR User");
        return r;
    }

    @Test
    void create_returnsCreatedHr() {
        UUID id = UUID.randomUUID();
        HRCreateRequest req = new HRCreateRequest();
        when(hrService.create(req)).thenReturn(hrResponse(id));

        ResponseEntity<ResponseData<HRResponse>> resp = controller.create(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData().getUserId()).isEqualTo(id);
        verify(hrService).create(req);
    }

    @Test
    void update_returnsUpdatedHr() {
        UUID id = UUID.randomUUID();
        HRUpdateRequest req = new HRUpdateRequest();
        when(hrService.update(id, req)).thenReturn(hrResponse(id));

        ResponseEntity<ResponseData<HRResponse>> resp = controller.update(id, req);

        assertThat(resp.getBody().getData().getUserId()).isEqualTo(id);
    }

    @Test
    void delete_callsServiceAndReturnsOk() {
        UUID id = UUID.randomUUID();
        doNothing().when(hrService).delete(id);

        ResponseEntity<ResponseData<Void>> resp = controller.delete(id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(hrService).delete(id);
    }

    @Test
    void getById_returnsHr() {
        UUID id = UUID.randomUUID();
        when(hrService.getById(id)).thenReturn(hrResponse(id));

        ResponseEntity<ResponseData<HRResponse>> resp = controller.getById(id);

        assertThat(resp.getBody().getData().getUserId()).isEqualTo(id);
    }

    @Test
    void search_returnsPage() {
        UUID id = UUID.randomUUID();
        Page<HRResponse> page = new PageImpl<>(List.of(hrResponse(id)));
        when(hrService.search(null, Pageable.unpaged())).thenReturn(page);

        ResponseEntity<ResponseData<Page<HRResponse>>> resp =
                controller.search(null, Pageable.unpaged());

        assertThat(resp.getBody().getData().getTotalElements()).isEqualTo(1);
    }

    @Test
    void approveManager_delegatesToService() {
        UUID id = UUID.randomUUID();
        when(hrService.approveHrManager(id, "admin1")).thenReturn(hrResponse(id));

        ResponseEntity<ResponseData<HRResponse>> resp = controller.approveManager(id, "admin1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(hrService).approveHrManager(id, "admin1");
    }

    @Test
    void rejectManager_delegatesToService() {
        UUID id = UUID.randomUUID();
        when(hrService.rejectHrManager(id, "admin1")).thenReturn(hrResponse(id));

        ResponseEntity<ResponseData<HRResponse>> resp = controller.rejectManager(id, "admin1");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(hrService).rejectHrManager(id, "admin1");
    }

    @Test
    void getByUsername_returnsHr() {
        UUID id = UUID.randomUUID();
        when(hrService.getByUsername("hr_user")).thenReturn(hrResponse(id));

        ResponseEntity<ResponseData<HRResponse>> resp = controller.getByUsername("hr_user");

        assertThat(resp.getBody().getData().getFullName()).isEqualTo("HR User");
    }
}
