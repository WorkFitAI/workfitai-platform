package org.workfitai.applicationservice.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final ApplicationRepository applicationRepository;

    /**
     * Add a new note to an application.
     *
     * @param applicationId Application ID
     * @param request       Note details
     * @param author        Username of note author
     * @return Created note
     */
    @Transactional
    public NoteResponse addNote(String applicationId, CreateNoteRequest request, String author) {
        log.info("Adding note to application: id={}, author={}", applicationId, author);

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        Application.Note note = Application.Note.builder()
                .id(UUID.randomUUID().toString())
                .author(author)
                .content(sanitizeContent(request.getContent()))
                .candidateVisible(request.isCandidateVisible())
                .createdAt(Instant.now())
                .build();

        application.getNotes().add(note);
        applicationRepository.save(application);

        log.info("Note added successfully: noteId={}", note.getId());
        return toResponse(note);
    }

    /**
     * Update an existing note.
     *
     * @param applicationId Application ID
     * @param noteId        Note ID
     * @param request       Update details
     * @param username      Username (for authorization)
     * @return Updated note
     */
    @Transactional
    public NoteResponse updateNote(String applicationId, String noteId, UpdateNoteRequest request, String username) {
        log.info("Updating note: appId={}, noteId={}, user={}", applicationId, noteId, username);

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        Application.Note note = application.getNotes().stream()
                .filter(n -> n.getId().equals(noteId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Note not found"));

        // Verify author
        if (!note.getAuthor().equals(username)) {
            throw new ForbiddenException("Only the note author can update it");
        }

        // Update fields if provided
        if (request.getContent() != null) {
            note.setContent(sanitizeContent(request.getContent()));
        }
        if (request.getCandidateVisible() != null) {
            note.setCandidateVisible(request.getCandidateVisible());
        }
        note.setUpdatedAt(Instant.now());

        applicationRepository.save(application);

        log.info("Note updated successfully: noteId={}", noteId);
        return toResponse(note);
    }

    /**
     * Delete a note.
     *
     * @param applicationId Application ID
     * @param noteId        Note ID
     * @param username      Username (for authorization)
     */
    @Transactional
    public void deleteNote(String applicationId, String noteId, String username) {
        log.info("Deleting note: appId={}, noteId={}, user={}", applicationId, noteId, username);

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        Application.Note note = application.getNotes().stream()
                .filter(n -> n.getId().equals(noteId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Note not found"));

        // Verify author
        if (!note.getAuthor().equals(username)) {
            throw new ForbiddenException("Only the note author can delete it");
        }

        application.getNotes().remove(note);
        applicationRepository.save(application);

        log.info("Note deleted successfully: noteId={}", noteId);
    }

    /**
     * Get all notes for an application (HR view).
     *
     * @param applicationId Application ID
     * @return List of all notes
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
     *
     * @param applicationId Application ID
     * @return List of public notes only
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

    /**
     * Sanitize note content to prevent XSS.
     *
     * @param content Raw content
     * @return Sanitized content
     */
    private String sanitizeContent(String content) {
        // Strip HTML tags to prevent XSS
        return content.replaceAll("<[^>]*>", "");
    }

    /**
     * Convert Note entity to NoteResponse DTO.
     *
     * @param note Note entity
     * @return NoteResponse DTO
     */
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
