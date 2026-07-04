package org.workfitai.cvservice.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.cvservice.errors.CVConflictException;
import org.workfitai.cvservice.errors.InvalidDataException;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;
import org.workfitai.cvservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.cvservice.model.enums.TemplateType;
import org.workfitai.cvservice.service.iCVService;

import java.io.ByteArrayInputStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CVController.class)
@AutoConfigureMockMvc(addFilters = false)
class CVControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private iCVService service;

    @Test
    void createCV_returnsCreated() throws Exception {
        ResCvDTO dto = ResCvDTO.builder().cvId("cv1").headline("Backend Engineer").build();
        when(service.createCv(eq("template"), any())).thenReturn(dto);

        mockMvc.perform(post("/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Backend Engineer\",\"summary\":\"sum\",\"templateType\":\"TECH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cvId").value("cv1"));
    }

    @Test
    void createCV_returns400_whenHeadlineBlank() throws Exception {
        mockMvc.perform(post("/candidate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"\",\"summary\":\"sum\",\"templateType\":\"TECH\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFromUpload_returnsOk() throws Exception {
        ResCvDTO dto = ResCvDTO.builder().cvId("cv2").templateType(TemplateType.UPLOAD).build();
        when(service.createCv(eq("upload"), any())).thenReturn(dto);

        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[]{1});

        mockMvc.perform(multipart("/candidate/upload")
                        .file(file)
                        .param("templateType", "UPLOAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvId").value("cv2"));
    }

    @Test
    void getCV_returnsOk() throws Exception {
        ResCvDTO dto = ResCvDTO.builder().cvId("cv1").build();
        when(service.getById("cv1")).thenReturn(dto);

        mockMvc.perform(get("/candidate/id/cv1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvId").value("cv1"));
    }

    @Test
    void getCV_propagatesNotFound_whenServiceThrows() throws Exception {
        when(service.getById("missing")).thenThrow(new org.workfitai.cvservice.errors.ResourceNotFoundException("not found"));

        mockMvc.perform(get("/candidate/id/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCVsByUsernameWithFilter_usesDefaultPageAndSize() throws Exception {
        ResultPaginationDTO<ResCvDTO> page = ResultPaginationDTO.<ResCvDTO>builder().result(java.util.List.of()).build();
        when(service.getCVByBelongToWithFilter(eq("alice"), anyMap(), eq(0), eq(10))).thenReturn(page);

        mockMvc.perform(get("/candidate/alice"))
                .andExpect(status().isOk());

        verify(service).getCVByBelongToWithFilter(eq("alice"), anyMap(), eq(0), eq(10));
    }

    @Test
    void getCVsByUsernameWithFilter_usesProvidedPageAndSize() throws Exception {
        ResultPaginationDTO<ResCvDTO> page = ResultPaginationDTO.<ResCvDTO>builder().result(java.util.List.of()).build();
        when(service.getCVByBelongToWithFilter(eq("alice"), anyMap(), eq(2), eq(5))).thenReturn(page);

        mockMvc.perform(get("/candidate/alice").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(service).getCVByBelongToWithFilter(eq("alice"), anyMap(), eq(2), eq(5));
    }

    @Test
    void updateCV_returnsOk() throws Exception {
        ResCvDTO dto = ResCvDTO.builder().cvId("cv1").headline("Updated").build();
        when(service.update(eq("cv1"), any())).thenReturn(dto);

        mockMvc.perform(patch("/candidate/cv1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Updated\",\"pdfUrl\":\"http://x.com/a.pdf\",\"templateType\":\"TECH\",\"sections\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headline").value("Updated"));
    }

    @Test
    void updateCV_returns409_whenConflict() throws Exception {
        when(service.update(eq("cv1"), any())).thenThrow(new CVConflictException("conflict"));

        mockMvc.perform(patch("/candidate/cv1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"headline\":\"Updated\",\"pdfUrl\":\"http://x.com/a.pdf\",\"templateType\":\"TECH\",\"sections\":{}}"))
                .andExpect(status().isConflict());
    }

    @Test
    void deleteCV_returnsOk() throws Exception {
        mockMvc.perform(delete("/candidate/cv1"))
                .andExpect(status().isOk());

        verify(service).delete("cv1");
    }

    @Test
    void deleteCVById_returnsOk() throws Exception {
        mockMvc.perform(delete("/cv1"))
                .andExpect(status().isOk());

        verify(service).delete("cv1");
    }

    @Test
    void downloadCv_returnsOk() throws Exception {
        when(service.downloadCV("file.pdf")).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        mockMvc.perform(get("/candidate/download/file.pdf"))
                .andExpect(status().isOk())
                .andExpect(result -> result.getResponse().getHeader("Content-Disposition").contains("file.pdf"));
    }
}
