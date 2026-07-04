package org.workfitai.monitoringservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.monitoringservice.dto.AuditPatternDto;
import org.workfitai.monitoringservice.dto.AuditPatternUpdateRequest;
import org.workfitai.monitoringservice.service.AuditPatternService;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditPatternController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuditPatternControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private AuditPatternService auditPatternService;

    @Test
    void getAllPatterns_returnsList() throws Exception {
        when(auditPatternService.getAllPatterns()).thenReturn(
                List.of(new AuditPatternDto("CREATE_Job", "created a job", "created a job", false)));

        mockMvc.perform(get("/api/admin/audit/patterns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("CREATE_Job"));
    }

    @Test
    void getPattern_returnsOk_whenFound() throws Exception {
        when(auditPatternService.getPattern("CREATE_Job")).thenReturn(
                Optional.of(new AuditPatternDto("CREATE_Job", "created a job", "created a job", false)));

        mockMvc.perform(get("/api/admin/audit/patterns/CREATE_Job"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("created a job"));
    }

    @Test
    void getPattern_returnsNotFound_whenAbsent() throws Exception {
        when(auditPatternService.getPattern("UNKNOWN")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/audit/patterns/UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void updatePattern_returnsOk_onSuccess() throws Exception {
        when(auditPatternService.updatePattern(eq("CREATE_Job"), eq("did it")))
                .thenReturn(new AuditPatternDto("CREATE_Job", "did it", "created a job", true));

        mockMvc.perform(put("/api/admin/audit/patterns/CREATE_Job")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuditPatternUpdateRequest("did it"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(true));
    }

    @Test
    void updatePattern_returnsBadRequest_onInvalidKey() throws Exception {
        when(auditPatternService.updatePattern(eq("UNKNOWN"), eq("x")))
                .thenThrow(new IllegalArgumentException("unknown pattern key"));

        mockMvc.perform(put("/api/admin/audit/patterns/UNKNOWN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuditPatternUpdateRequest("x"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unknown pattern key"));
    }

    @Test
    void resetPattern_returnsOk_onSuccess() throws Exception {
        when(auditPatternService.resetPattern("CREATE_Job"))
                .thenReturn(new AuditPatternDto("CREATE_Job", "created a job", "created a job", false));

        mockMvc.perform(delete("/api/admin/audit/patterns/CREATE_Job"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customized").value(false));
    }

    @Test
    void resetPattern_returnsBadRequest_onInvalidKey() throws Exception {
        when(auditPatternService.resetPattern("UNKNOWN"))
                .thenThrow(new IllegalArgumentException("unknown pattern key"));

        mockMvc.perform(delete("/api/admin/audit/patterns/UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetAll_returnsOkAndCallsService() throws Exception {
        mockMvc.perform(post("/api/admin/audit/patterns/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());

        verify(auditPatternService).resetAll();
    }
}
