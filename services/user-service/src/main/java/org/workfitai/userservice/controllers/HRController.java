package org.workfitai.userservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.constants.Messages;
import org.workfitai.userservice.dto.request.HRCreateRequest;
import org.workfitai.userservice.dto.request.HRUpdateRequest;
import org.workfitai.userservice.dto.response.HRResponse;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.services.HRService;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/hr")
@RequiredArgsConstructor
public class HRController {

  private final HRService hrService;

  @PostMapping
  public ResponseEntity<ResponseData<HRResponse>> create(@RequestBody HRCreateRequest dto) {
    return ResponseEntity.ok(ResponseData.success(
        Messages.HR.CREATED, hrService.create(dto)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ResponseData<HRResponse>> update(@PathVariable UUID id, @RequestBody HRUpdateRequest dto) {
    return ResponseEntity.ok(ResponseData.success(
        Messages.HR.UPDATED, hrService.update(id, dto)));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ResponseData<Void>> delete(@PathVariable UUID id) {
    hrService.delete(id);
    return ResponseEntity.ok(ResponseData.success(
        Messages.HR.DELETED, null));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ResponseData<HRResponse>> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(ResponseData.success(hrService.getById(id)));
  }

  @GetMapping
  public ResponseEntity<ResponseData<Page<HRResponse>>> search(
      @RequestParam(required = false) String keyword, Pageable pageable) {
    return ResponseEntity.ok(ResponseData.success(hrService.search(keyword, pageable)));
  }

  @GetMapping("/stats/department")
  public ResponseEntity<ResponseData<Map<String, Long>>> countByDepartment() {
    return ResponseEntity.ok(ResponseData.success(hrService.countByDepartment()));
  }
}
