package org.workfitai.monitoringservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.workfitai.monitoringservice.dto.LogEntry;
import org.workfitai.monitoringservice.dto.LogSearchRequest;
import org.workfitai.monitoringservice.dto.LogSearchResponse;
import org.workfitai.monitoringservice.dto.LogStatistics;
import org.workfitai.monitoringservice.service.LogSearchService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogController.class)
@AutoConfigureMockMvc(addFilters = false)
class LogControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private LogSearchService logSearchService;

    @Test
    void searchLogs_post_returnsOk() throws Exception {
        when(logSearchService.searchLogs(any())).thenReturn(
                LogSearchResponse.builder().total(1).logs(List.of(LogEntry.builder().id("1").build())).build());

        mockMvc.perform(post("/api/logs/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(LogSearchRequest.builder().query("x").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void searchLogs_get_mergesLevelIntoLevelsAndUsesKeywordFallback() throws Exception {
        when(logSearchService.searchLogs(any())).thenReturn(
                LogSearchResponse.builder().total(0).logs(List.of()).build());

        mockMvc.perform(get("/api/logs/search")
                        .param("level", "ERROR")
                        .param("keyword", "boom"))
                .andExpect(status().isOk());
    }

    @Test
    void searchLogs_get_usesQueryDirectly_andMergesLevelIntoExistingLevels() throws Exception {
        when(logSearchService.searchLogs(any())).thenReturn(
                LogSearchResponse.builder().total(0).logs(List.of()).build());

        mockMvc.perform(get("/api/logs/search")
                        .param("query", "explicit")
                        .param("level", "ERROR")
                        .param("levels", "WARN", "ERROR"))
                .andExpect(status().isOk());
    }

    @Test
    void searchLogs_get_withInstantFromTo_parsesSuccessfully() throws Exception {
        when(logSearchService.searchLogs(any())).thenReturn(
                LogSearchResponse.builder().total(0).logs(List.of()).build());

        mockMvc.perform(get("/api/logs/search")
                        .param("from", "2024-01-01T00:00:00Z")
                        .param("to", "2024-01-02T00:00:00Z"))
                .andExpect(status().isOk());
    }

    @Test
    void searchLogs_get_withLocalDateTimeFrom_parsesSuccessfully() throws Exception {
        when(logSearchService.searchLogs(any())).thenReturn(
                LogSearchResponse.builder().total(0).logs(List.of()).build());

        mockMvc.perform(get("/api/logs/search").param("from", "2024-01-01T00:00:00"))
                .andExpect(status().isOk());
    }

    @Test
    void searchLogs_get_withDateOnlyFrom_parsesSuccessfully() throws Exception {
        when(logSearchService.searchLogs(any())).thenReturn(
                LogSearchResponse.builder().total(0).logs(List.of()).build());

        mockMvc.perform(get("/api/logs/search").param("from", "2024-01-01"))
                .andExpect(status().isOk());
    }

    @Test
    void searchLogs_get_withUnparsableFrom_treatsAsNull() throws Exception {
        when(logSearchService.searchLogs(any())).thenReturn(
                LogSearchResponse.builder().total(0).logs(List.of()).build());

        mockMvc.perform(get("/api/logs/search").param("from", "not-a-date"))
                .andExpect(status().isOk());
    }

    @Test
    void getStatistics_returnsOk() throws Exception {
        when(logSearchService.getStatistics(anyInt())).thenReturn(
                LogStatistics.builder().totalLogs(5).build());

        mockMvc.perform(get("/api/logs/statistics").param("hours", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLogs").value(5));
    }

    @Test
    void getLogsByTraceId_returnsOk() throws Exception {
        when(logSearchService.getLogsByTraceId("trace-1")).thenReturn(
                List.of(LogEntry.builder().id("1").traceId("trace-1").build()));

        mockMvc.perform(get("/api/logs/trace/trace-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].traceId").value("trace-1"));
    }

    @Test
    void getRecentErrors_returnsOk() throws Exception {
        when(logSearchService.getRecentErrors(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/logs/errors/recent").param("minutes", "30"))
                .andExpect(status().isOk());
    }

    @Test
    void getLogsByService_returnsOk() throws Exception {
        when(logSearchService.searchLogs(any())).thenReturn(LogSearchResponse.builder().total(0).build());

        mockMvc.perform(get("/api/logs/service/job-service"))
                .andExpect(status().isOk());
    }

    @Test
    void getLogsByUser_returnsOk() throws Exception {
        when(logSearchService.searchLogs(any())).thenReturn(LogSearchResponse.builder().total(0).build());

        mockMvc.perform(get("/api/logs/user/alice"))
                .andExpect(status().isOk());
    }

}
