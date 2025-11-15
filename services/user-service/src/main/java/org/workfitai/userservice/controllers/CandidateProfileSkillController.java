package org.workfitai.userservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.constants.Messages;
import org.workfitai.userservice.dto.request.CandidateProfileSkillRequest;
import org.workfitai.userservice.dto.request.CandidateProfileSkillUpdateRequest;
import org.workfitai.userservice.dto.response.CandidateProfileSkillResponse;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.services.CandidateProfileSkillService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/candidate-skills")
@RequiredArgsConstructor
public class CandidateProfileSkillController {

  private final CandidateProfileSkillService candidateSkillService;

  @PostMapping("/candidate/{candidateId}")
  public ResponseEntity<ResponseData<CandidateProfileSkillResponse>> create(
      @PathVariable UUID candidateId,
      @RequestBody CandidateProfileSkillRequest dto) {
    return ResponseEntity.ok(ResponseData.success(
        Messages.CandidateSkill.CREATED,
        candidateSkillService.create(candidateId, dto)
    ));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ResponseData<CandidateProfileSkillResponse>> update(
      @PathVariable UUID id,
      @RequestBody CandidateProfileSkillUpdateRequest dto) {
    return ResponseEntity.ok(ResponseData.success(
        Messages.CandidateSkill.UPDATED,
        candidateSkillService.update(id, dto)
    ));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ResponseData<Void>> delete(@PathVariable UUID id) {
    candidateSkillService.delete(id);
    return ResponseEntity.ok(ResponseData.success(Messages.CandidateSkill.DELETED, null));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ResponseData<CandidateProfileSkillResponse>> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(ResponseData.success(
        Messages.CandidateSkill.FETCHED,
        candidateSkillService.getById(id)
    ));
  }

  @GetMapping("/candidate/{candidateId}")
  public ResponseEntity<ResponseData<List<CandidateProfileSkillResponse>>> getByCandidateId(
      @PathVariable UUID candidateId) {
    return ResponseEntity.ok(ResponseData.success(
        Messages.CandidateSkill.FETCHED,
        candidateSkillService.getAllByCandidate(candidateId)
    ));
  }
}
