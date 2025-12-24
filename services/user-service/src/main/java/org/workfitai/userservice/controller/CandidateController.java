package org.workfitai.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.annotation.CheckPrivacy;
import org.workfitai.userservice.constants.Messages;
import org.workfitai.userservice.dto.request.CandidateCreateRequest;
import org.workfitai.userservice.dto.request.CandidateUpdateRequest;
import org.workfitai.userservice.dto.response.CandidateResponse;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.service.CandidateService;

import java.util.UUID;

@RestController
@RequestMapping("/candidates")
@RequiredArgsConstructor
public class CandidateController {

    private final CandidateService candidateService;

    @PostMapping
    @PreAuthorize("hasAuthority('candidate:create')")
    public ResponseEntity<ResponseData<CandidateResponse>> create(@RequestBody CandidateCreateRequest dto) {
        CandidateResponse response = candidateService.create(dto);
        return ResponseEntity.ok(ResponseData.success(
                Messages.Candidate.CREATED, response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('candidate:update')")
    public ResponseEntity<ResponseData<CandidateResponse>> update(
            @PathVariable UUID id, @RequestBody CandidateUpdateRequest dto) {
        CandidateResponse response = candidateService.update(id, dto);
        return ResponseEntity.ok(ResponseData.success(
                Messages.Candidate.UPDATED, response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('candidate:delete')")
    public ResponseEntity<ResponseData<Void>> delete(@PathVariable UUID id) {
        candidateService.delete(id);
        return ResponseEntity.ok(ResponseData.success(
                Messages.Candidate.DELETED, null));
    }

    @CheckPrivacy
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('candidate:read')")
    public ResponseEntity<ResponseData<CandidateResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ResponseData.success(candidateService.getById(id)));
    }

    @CheckPrivacy
    @GetMapping
    @PreAuthorize("hasAuthority('candidate:search')")
    public ResponseEntity<ResponseData<Page<CandidateResponse>>> search(
            @RequestParam(required = false) String keyword, Pageable pageable) {
        Page<CandidateResponse> result = candidateService.search(keyword, pageable);
        return ResponseEntity.ok(ResponseData.success(result));
    }

    @GetMapping("/stats/experience")
    @PreAuthorize("hasAuthority('candidate:read')")
    public ResponseEntity<ResponseData<Object>> getExperienceStats() {
        return ResponseEntity.ok(ResponseData.success(candidateService.getExperienceStats()));
    }
}
