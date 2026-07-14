package org.workfitai.applicationservice.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.dto.request.CreateNoteRequest;
import org.workfitai.applicationservice.dto.request.UpdateNoteRequest;
import org.workfitai.applicationservice.dto.response.NoteResponse;
import org.workfitai.applicationservice.exception.ForbiddenException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing HR notes on applications.
 *
 * Notes allow HR/recruiters to collaborate and document decisions.
 * Notes can be marked as visible to candidates for transparency.
 *
 * Authorization:
 * - Add note: HR with application:note permission
 * - Update/Delete: Author only
 * - View all: HR with application:review permission
 * - View public: Candidate (via separate endpoint)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationNoteService {

    /** Note.content is capped at 2000 chars (see Application.Note); truncate defensively for system-generated content. */
    private static final int NOTE_CONTENT_MAX_LENGTH = 2000;

    private final ApplicationRepository applicationRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Add a new note to an application.
     * Uses atomic $push to prevent note loss under concurrent additions (H2 fix).
     */
    public NoteResponse addNote(String applicationId, CreateNoteRequest request, String author) {
        log.info("Adding note to application: id={}, author={}", applicationId, author);

        // Verify application exists before attempting update
        applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        Application.Note note = Application.Note.builder()
                .id(UUID.randomUUID().toString())
                .author(author)
                .content(request.getContent())
                .candidateVisible(request.isCandidateVisible())
                .createdAt(Instant.now())
                .build();

        // Atomic $push — no race condition on concurrent note additions
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(applicationId).and("deletedAt").isNull()),
                new Update().push("notes", note),
                Application.class);

        log.info("Note added successfully: noteId={}", note.getId());
        return toResponse(note);
    }

    /**
     * Update an existing note.
     * Uses atomic $set with array filters to prevent note loss under concurrent updates.
     */
    public NoteResponse updateNote(String applicationId, String noteId, UpdateNoteRequest request, String username) {
        log.info("Updating note: appId={}, noteId={}, user={}", applicationId, noteId, username);

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        Application.Note note = application.getNotes().stream()
                .filter(n -> n.getId().equals(noteId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Note not found"));

        if (!note.getAuthor().equals(username)) {
            throw new ForbiddenException("Only the note author can update it");
        }

        Update update = new Update();
        if (request.getContent() != null) {
            update.set("notes.$[elem].content", request.getContent());
        }
        if (request.getCandidateVisible() != null) {
            update.set("notes.$[elem].candidateVisible", request.getCandidateVisible());
        }
        update.set("notes.$[elem].updatedAt", Instant.now());

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(applicationId).and("deletedAt").isNull()),
                update.filterArray(Criteria.where("elem.id").is(noteId)),
                Application.class);

        Application updated = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        log.info("Note updated successfully: noteId={}", noteId);
        return updated.getNotes().stream()
                .filter(n -> n.getId().equals(noteId))
                .findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Note not found after update"));
    }

    /**
     * Delete a note.
     * Uses atomic $pull after authorship check to prevent double-delete races.
     */
    public void deleteNote(String applicationId, String noteId, String username) {
        log.info("Deleting note: appId={}, noteId={}, user={}", applicationId, noteId, username);

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        Application.Note note = application.getNotes().stream()
                .filter(n -> n.getId().equals(noteId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Note not found"));

        if (!note.getAuthor().equals(username)) {
            throw new ForbiddenException("Only the note author can delete it");
        }

        // Atomic $pull by noteId — safe even under concurrent access
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(applicationId).and("deletedAt").isNull()),
                new Update().pull("notes", new Document("id", noteId)),
                Application.class);

        log.info("Note deleted successfully: noteId={}", noteId);
    }

    /**
     * Adds a system-generated note (e.g. AI ranking explanation) to an already-loaded
     * Application, but only if no existing note has the exact same content. Prevents
     * duplicate notes from piling up when ranking is re-run and the AI explanation is
     * unchanged.
     */
    public void addNoteIfContentIsNew(Application application, String content, String author) {
        if (content == null || content.isBlank()) {
            return;
        }

        String truncated = content.length() > NOTE_CONTENT_MAX_LENGTH
                ? content.substring(0, NOTE_CONTENT_MAX_LENGTH) : content;

        boolean alreadyExists = application.getNotes().stream()
                .anyMatch(n -> n.getContent().equals(truncated));
        if (alreadyExists) {
            return;
        }

        Application.Note note = Application.Note.builder()
                .id(UUID.randomUUID().toString())
                .author(author)
                .content(truncated)
                .candidateVisible(false)
                .createdAt(Instant.now())
                .build();

        // Atomic $push — no race condition on concurrent note additions
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(application.getId()).and("deletedAt").isNull()),
                new Update().push("notes", note),
                Application.class);

        log.info("System note added from ranking explanation: appId={}, noteId={}", application.getId(), note.getId());
    }

    /**
     * Get all notes for an application (HR view).
     */
    public List<NoteResponse> getAllNotes(String applicationId) {
        log.debug("Fetching all notes for application: id={}", applicationId);

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        return application.getNotes().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get public notes for an application (candidate view).
     */
    public List<NoteResponse> getPublicNotes(String applicationId) {
        log.debug("Fetching public notes for application: id={}", applicationId);

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        return application.getNotes().stream()
                .filter(Application.Note::isCandidateVisible)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private NoteResponse toResponse(Application.Note note) {
        return NoteResponse.builder()
                .id(note.getId())
                .author(note.getAuthor())
                .content(note.getContent())
                .candidateVisible(note.isCandidateVisible())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
