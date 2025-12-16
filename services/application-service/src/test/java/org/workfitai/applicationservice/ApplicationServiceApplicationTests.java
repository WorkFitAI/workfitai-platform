package org.workfitai.applicationservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.applicationservice.dto.request.AssignApplicationRequest;
import org.workfitai.applicationservice.dto.request.CreateNoteRequest;
import org.workfitai.applicationservice.dto.request.UpdateNoteRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.NoteResponse;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.security.ApplicationSecurity;
import org.workfitai.applicationservice.service.ApplicationNoteService;
import org.workfitai.applicationservice.service.AssignmentService;
import org.workfitai.applicationservice.service.IDraftApplicationService;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("Application Service Integration Tests")
class ApplicationServiceApplicationTests {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private IDraftApplicationService draftApplicationService;

        @MockBean
        private ApplicationNoteService applicationNoteService;

        @MockBean
        private AssignmentService assignmentService;

        @MockBean
        private ApplicationSecurity applicationSecurity;

        @MockBean
        private JwtDecoder jwtDecoder;

        private static final String USERNAME = "john.doe";
        private static final String HR_USERNAME = "hr.manager";
        private static final String JOB_ID = "job-12345";
        private static final String APP_ID = "app-67890";
        private static final String NOTE_ID = "note-abc";

        private ApplicationResponse draftResponse;
        private NoteResponse noteResponse;

        @BeforeEach
        void setUp() {
                // Mock security context
                given(applicationSecurity.getCurrentUsername(any())).willReturn(USERNAME);

                // Build test fixtures
                draftResponse = ApplicationResponse.builder()
                                .id(APP_ID)
                                .username(USERNAME)
                                .jobId(JOB_ID)
                                .status(ApplicationStatus.DRAFT)
                                .coverLetter("Draft cover letter")
                                .createdAt(Instant.now())
                                .build();

                noteResponse = NoteResponse.builder()
                                .id(NOTE_ID)
                                .author(HR_USERNAME)
                                .content("Test note content")
                                .candidateVisible(false)
                                .createdAt(Instant.now())
                                .build();
        }

        @Test
        void contextLoads() {
                // Keep existing test
        }

        @Test
        @DisplayName("Should create draft application successfully")
        void testCreateDraft() throws Exception {
                // Given: Mock service response
                given(draftApplicationService.createDraft(
                                eq(JOB_ID),
                                anyString(),
                                anyString(),
                                any(),
                                eq(USERNAME))).willReturn(draftResponse);

                // When: POST multipart request
                mockMvc.perform(multipart("/api/v1/applications/draft")
                                .param("jobId", JOB_ID)
                                .param("email", "john@example.com")
                                .param("coverLetter", "Draft cover letter")
                                .with(jwt().jwt(jwt -> jwt.subject(USERNAME))
                                                .authorities(new SimpleGrantedAuthority("application:create"))))
                                // Then: Expect 201 with draft response
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.id").value(APP_ID))
                                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                                .andExpect(jsonPath("$.data.username").value(USERNAME));

                // Verify service called
                verify(draftApplicationService).createDraft(
                                eq(JOB_ID),
                                anyString(),
                                anyString(),
                                any(),
                                eq(USERNAME));
        }

        @Test
        @DisplayName("Should update draft application successfully")
        void testUpdateDraft() throws Exception {
                // Given: Mock security check and service
                given(applicationSecurity.canEditDraft(eq(APP_ID), any())).willReturn(true);
                given(draftApplicationService.updateDraft(
                                eq(APP_ID),
                                anyString(),
                                anyString(),
                                any(),
                                eq(USERNAME))).willReturn(draftResponse);

                // When: PUT multipart request
                mockMvc.perform(multipart("/api/v1/applications/" + APP_ID + "/draft")
                                .with(request -> {
                                        request.setMethod("PUT");
                                        return request;
                                })
                                .param("email", "updated@example.com")
                                .param("coverLetter", "Updated cover letter")
                                .with(jwt().jwt(jwt -> jwt.subject(USERNAME))
                                                .authorities(new SimpleGrantedAuthority("application:create"))))
                                // Then: Expect 200 with updated draft
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.id").value(APP_ID))
                                .andExpect(jsonPath("$.data.status").value("DRAFT"));

                // Verify
                verify(applicationSecurity).canEditDraft(eq(APP_ID), any());
                verify(draftApplicationService).updateDraft(eq(APP_ID), anyString(), anyString(), any(), eq(USERNAME));
        }

        @Test
        @DisplayName("Should submit draft application successfully")
        void testSubmitDraft() throws Exception {
                // Given: Mock security and service
                given(applicationSecurity.canEditDraft(eq(APP_ID), any())).willReturn(true);

                ApplicationResponse submittedResponse = ApplicationResponse.builder()
                                .id(APP_ID)
                                .username(USERNAME)
                                .jobId(JOB_ID)
                                .status(ApplicationStatus.APPLIED) // Status changed
                                .cvFileUrl("http://minio:9000/cvs/test.pdf")
                                .cvFileName("test.pdf")
                                .createdAt(Instant.now())
                                .build();

                given(draftApplicationService.submitDraft(
                                eq(APP_ID),
                                any(),
                                eq(USERNAME))).willReturn(submittedResponse);

                // When: POST multipart submit
                mockMvc.perform(multipart("/api/v1/applications/" + APP_ID + "/submit")
                                .file(new MockMultipartFile("cvPdfFile", "test.pdf", "application/pdf",
                                                "CV content".getBytes()))
                                .with(jwt().jwt(jwt -> jwt.subject(USERNAME))
                                                .authorities(new SimpleGrantedAuthority("application:create"))))
                                // Then: Expect 200 with APPLIED status
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.id").value(APP_ID))
                                .andExpect(jsonPath("$.data.status").value("APPLIED"));

                // Verify
                verify(applicationSecurity).canEditDraft(eq(APP_ID), any());
                verify(draftApplicationService).submitDraft(eq(APP_ID), any(), eq(USERNAME));
        }

        @Test
        @DisplayName("Should create note on application successfully")
        void testCreateNote() throws Exception {
                // Given: Mock security and service
                given(applicationSecurity.getCurrentUsername(any())).willReturn(HR_USERNAME);

                CreateNoteRequest request = CreateNoteRequest.builder()
                                .content("Initial HR review note")
                                .candidateVisible(false)
                                .build();

                given(applicationNoteService.addNote(
                                eq(APP_ID),
                                any(CreateNoteRequest.class),
                                eq(HR_USERNAME))).willReturn(noteResponse);

                // When: POST note
                mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/notes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(jwt().jwt(jwt -> jwt.subject(HR_USERNAME))
                                                .authorities(new SimpleGrantedAuthority("application:note"))))
                                // Then: Expect 201 with note
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.id").value(NOTE_ID))
                                .andExpect(jsonPath("$.data.author").value(HR_USERNAME))
                                .andExpect(jsonPath("$.data.content").value("Test note content"))
                                .andExpect(jsonPath("$.data.candidateVisible").value(false));

                // Verify
                verify(applicationNoteService).addNote(eq(APP_ID), any(CreateNoteRequest.class), eq(HR_USERNAME));
        }

        @Test
        @DisplayName("Should update note successfully")
        void testUpdateNote() throws Exception {
                // Given: Mock security and service
                given(applicationSecurity.isNoteAuthor(eq(APP_ID), eq(NOTE_ID), any())).willReturn(true);
                given(applicationSecurity.getCurrentUsername(any())).willReturn(HR_USERNAME);

                UpdateNoteRequest request = UpdateNoteRequest.builder()
                                .content("Updated note content")
                                .candidateVisible(true)
                                .build();

                NoteResponse updatedNote = NoteResponse.builder()
                                .id(NOTE_ID)
                                .author(HR_USERNAME)
                                .content("Updated note content")
                                .candidateVisible(true)
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                                .build();

                given(applicationNoteService.updateNote(
                                eq(APP_ID),
                                eq(NOTE_ID),
                                any(UpdateNoteRequest.class),
                                eq(HR_USERNAME))).willReturn(updatedNote);

                // When: PUT update note
                mockMvc.perform(put("/api/v1/applications/" + APP_ID + "/notes/" + NOTE_ID)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(jwt().jwt(jwt -> jwt.subject(HR_USERNAME))
                                                .authorities(new SimpleGrantedAuthority("application:note"))))
                                // Then: Expect 200 with updated note
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.id").value(NOTE_ID))
                                .andExpect(jsonPath("$.data.content").value("Updated note content"))
                                .andExpect(jsonPath("$.data.candidateVisible").value(true));

                // Verify
                verify(applicationSecurity).isNoteAuthor(eq(APP_ID), eq(NOTE_ID), any());
                verify(applicationNoteService).updateNote(eq(APP_ID), eq(NOTE_ID), any(UpdateNoteRequest.class),
                                eq(HR_USERNAME));
        }

        @Test
        @DisplayName("Should assign application to HR user successfully")
        void testAssignApplication() throws Exception {
                // Given: Mock security and service
                String managerUsername = "hr.manager";
                String assigneeUsername = "hr.recruiter";

                given(applicationSecurity.canAssign(eq(APP_ID), any())).willReturn(true);
                given(applicationSecurity.getCurrentUsername(any())).willReturn(managerUsername);

                AssignApplicationRequest request = AssignApplicationRequest.builder()
                                .assignedTo(assigneeUsername)
                                .build();

                ApplicationResponse assignedResponse = ApplicationResponse.builder()
                                .id(APP_ID)
                                .username(USERNAME)
                                .jobId(JOB_ID)
                                .status(ApplicationStatus.APPLIED)
                                .assignedTo(assigneeUsername)
                                .assignedAt(Instant.now())
                                .assignedBy(managerUsername)
                                .build();

                given(assignmentService.assignApplication(
                                eq(APP_ID),
                                eq(assigneeUsername),
                                eq(managerUsername))).willReturn(assignedResponse);

                // When: PUT assign
                mockMvc.perform(put("/api/v1/applications/" + APP_ID + "/assign")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                                .with(jwt().jwt(jwt -> jwt.subject(managerUsername))
                                                .authorities(new SimpleGrantedAuthority("application:update"))))
                                // Then: Expect 200 with assignment
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.id").value(APP_ID))
                                .andExpect(jsonPath("$.data.assignedTo").value(assigneeUsername))
                                .andExpect(jsonPath("$.data.assignedBy").value(managerUsername));

                // Verify
                verify(applicationSecurity).canAssign(eq(APP_ID), any());
                verify(assignmentService).assignApplication(eq(APP_ID), eq(assigneeUsername), eq(managerUsername));
        }
}
