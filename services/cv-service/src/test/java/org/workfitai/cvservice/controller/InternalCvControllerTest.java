package org.workfitai.cvservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.cvservice.model.dto.response.CvDataResponse;
import org.workfitai.cvservice.model.dto.response.CvSnapshotResponse;
import org.workfitai.cvservice.service.iCVService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalCvController.class)
@AutoConfigureMockMvc(addFilters = false)
class InternalCvControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private iCVService cvService;

    @Test
    void getCvDataBatch_returnsResultsForRequestedUsernames() throws Exception {
        when(cvService.getCvDataBatch(List.of("alice", "bob")))
                .thenReturn(List.of(CvDataResponse.builder().username("alice").build()));

        mockMvc.perform(post("/internal/cvs/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"alice\",\"bob\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    void createApplicationSnapshot_returnsSnapshotResponse() throws Exception {
        CvSnapshotResponse response = CvSnapshotResponse.builder().cvId("cv1").summary("sum").build();
        when(cvService.createApplicationSnapshot(eq("alice"), eq("app-1"), eq("Backend Engineer"), any()))
                .thenReturn(response);

        MockMultipartFile file = new MockMultipartFile("cvPdfFile", "resume.pdf", "application/pdf", new byte[]{1});

        mockMvc.perform(multipart("/internal/cvs/application-snapshot")
                        .file(file)
                        .part(new org.springframework.mock.web.MockPart("username", "alice".getBytes()))
                        .part(new org.springframework.mock.web.MockPart("applicationId", "app-1".getBytes()))
                        .part(new org.springframework.mock.web.MockPart("jobName", "Backend Engineer".getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvId").value("cv1"));
    }

    @Test
    void getCvSnapshotsByApplicationIds_returnsEmptyList_whenInputEmpty() throws Exception {
        mockMvc.perform(post("/internal/cvs/batch-by-application-ids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(cvService, never()).getCvSnapshotsByApplicationIds(any());
    }

    @Test
    void getCvSnapshotsByApplicationIds_returnsSnapshots_whenFound() throws Exception {
        when(cvService.getCvSnapshotsByApplicationIds(List.of("app-1")))
                .thenReturn(List.of(CvSnapshotResponse.builder().cvId("cv1").build()));

        mockMvc.perform(post("/internal/cvs/batch-by-application-ids")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[\"app-1\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cvId").value("cv1"));
    }
}
