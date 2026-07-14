package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.workfitai.applicationservice.dto.request.CreateNoteRequest;
import org.workfitai.applicationservice.dto.request.UpdateNoteRequest;
import org.workfitai.applicationservice.dto.response.NoteResponse;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class ApplicationNoteServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock MongoTemplate mongoTemplate;

    @InjectMocks ApplicationNoteService noteService;

    private Application application;
    private Application.Note note;

    @BeforeEach
    void setUp() {
        note = Application.Note.builder()
                .id("note-1")
                .author("hr1")
                .content("Initial note")
                .candidateVisible(false)
                .createdAt(Instant.now())
                .build();

        application = Application.builder()
                .id("app-1")
                .username("user1")
                .notes(new ArrayList<>(List.of(note)))
                .build();
    }

    // ─── addNote ──────────────────────────────────────────────────────────────

    @Test
    void addNote_applicationExists_pushesNoteAtomically() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        CreateNoteRequest req = new CreateNoteRequest();
        req.setContent("Good candidate");
        req.setCandidateVisible(true);

        NoteResponse response = noteService.addNote("app-1", req, "hr1");

        assertThat(response.getContent()).isEqualTo("Good candidate");
        assertThat(response.getAuthor()).isEqualTo("hr1");
        // Atomic $push via MongoTemplate
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Application.class));
    }

    @Test
    void addNote_applicationNotFound_throwsNotFoundException() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> noteService.addNote("bad", new CreateNoteRequest(), "hr1"))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── addNoteIfContentIsNew ──────────────────────────────────────────────────

    @Test
    void addNoteIfContentIsNew_newContent_pushesNoteAtomically() {
        noteService.addNoteIfContentIsNew(application, "AI explanation: match Java, miss K8s", "AI_RANKING");

        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Application.class));
    }

    @Test
    void addNoteIfContentIsNew_contentMatchesExistingNote_skipsPush() {
        // "Initial note" already exists on the application from setUp()
        noteService.addNoteIfContentIsNew(application, "Initial note", "AI_RANKING");

        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(Application.class));
    }

    @Test
    void addNoteIfContentIsNew_blankContent_doesNothing() {
        noteService.addNoteIfContentIsNew(application, "  ", "AI_RANKING");
        noteService.addNoteIfContentIsNew(application, null, "AI_RANKING");

        verifyNoInteractions(mongoTemplate);
    }

    // ─── updateNote ───────────────────────────────────────────────────────────

    @Test
    void updateNote_authorMatches_updatesAtomically() {
        Application.Note updatedNote = Application.Note.builder()
                .id("note-1").author("hr1").content("Updated content")
                .candidateVisible(true).createdAt(Instant.now()).updatedAt(Instant.now()).build();
        Application updatedApp = Application.builder()
                .id("app-1").username("user1")
                .notes(new ArrayList<>(List.of(updatedNote))).build();

        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1"))
                .thenReturn(Optional.of(application))
                .thenReturn(Optional.of(updatedApp));

        UpdateNoteRequest req = new UpdateNoteRequest();
        req.setContent("Updated content");
        req.setCandidateVisible(true);

        NoteResponse response = noteService.updateNote("app-1", "note-1", req, "hr1");

        assertThat(response.getContent()).isEqualTo("Updated content");
        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Application.class));
    }

    @Test
    void updateNote_wrongAuthor_throwsForbidden() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> noteService.updateNote("app-1", "note-1", new UpdateNoteRequest(), "other_hr"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateNote_noteNotFound_throwsNotFoundException() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> noteService.updateNote("app-1", "bad-note", new UpdateNoteRequest(), "hr1"))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── deleteNote ───────────────────────────────────────────────────────────

    @Test
    void deleteNote_authorMatches_pullsNoteAtomically() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        noteService.deleteNote("app-1", "note-1", "hr1");

        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(Application.class));
    }

    @Test
    void deleteNote_wrongAuthor_throwsForbidden() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> noteService.deleteNote("app-1", "note-1", "other_hr"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteNote_noteNotFound_throwsNotFoundException() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> noteService.deleteNote("app-1", "missing-note", "hr1"))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── getAllNotes / getPublicNotes ──────────────────────────────────────────

    @Test
    void getAllNotes_returnsAllNotes() {
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        List<NoteResponse> notes = noteService.getAllNotes("app-1");

        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).getId()).isEqualTo("note-1");
    }

    @Test
    void getPublicNotes_returnsOnlyCandidateVisibleNotes() {
        Application.Note publicNote = Application.Note.builder()
                .id("n2").author("hr1").content("Public").candidateVisible(true).createdAt(Instant.now()).build();
        application.setNotes(new ArrayList<>(List.of(note, publicNote)));
        when(applicationRepository.findByIdAndDeletedAtIsNull("app-1")).thenReturn(Optional.of(application));

        List<NoteResponse> notes = noteService.getPublicNotes("app-1");

        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).getId()).isEqualTo("n2");
    }
}
