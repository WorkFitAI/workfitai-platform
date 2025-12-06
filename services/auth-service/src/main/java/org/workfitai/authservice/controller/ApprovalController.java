package org.workfitai.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.authservice.dto.ApprovalRequest;
import org.workfitai.authservice.response.ResponseData;
import org.workfitai.authservice.service.ApprovalService;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/pending-approvals")
    public ResponseEntity<ResponseData<List<Object>>> getPendingApprovals() {
        List<Object> pendingApprovals = approvalService.getPendingApprovals();
        return ResponseEntity.ok(ResponseData.success("Pending approvals retrieved successfully", pendingApprovals));
    }

    @PostMapping("/approve-hr-manager/{userId}")
    public ResponseEntity<ResponseData<Void>> approveHRManager(
            @PathVariable String userId,
            @Valid @RequestBody ApprovalRequest request) {
        approvalService.approveHRManager(userId, request.getApprovedBy());
        return ResponseEntity.ok(ResponseData.success("HR Manager approved successfully"));
    }

    @PostMapping("/approve-hr/{userId}")
    public ResponseEntity<ResponseData<Void>> approveHR(
            @PathVariable String userId,
            @Valid @RequestBody ApprovalRequest request) {
        approvalService.approveHR(userId, request.getApprovedBy());
        return ResponseEntity.ok(ResponseData.success("HR approved successfully"));
    }

    @PostMapping("/reject/{userId}")
    public ResponseEntity<ResponseData<Void>> rejectUser(
            @PathVariable String userId,
            @Valid @RequestBody ApprovalRequest request) {
        approvalService.rejectUser(userId, request.getRejectedBy(), request.getReason());
        return ResponseEntity.ok(ResponseData.success("User registration rejected"));
    }
}
