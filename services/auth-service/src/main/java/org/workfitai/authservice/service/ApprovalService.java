package org.workfitai.authservice.service;

import java.util.List;

public interface ApprovalService {
    List<Object> getPendingApprovals();

    void approveHRManager(String userId, String approvedBy);

    void approveHR(String userId, String approvedBy);

    void rejectUser(String userId, String rejectedBy, String reason);
}