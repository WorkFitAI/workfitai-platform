package org.workfitai.jobservice.model.enums;

public enum EReportStatus {
    PENDING(0),
    IN_PROGRESS(1),
    RESOLVED(2),
    DECLINE(3);

    private final int order;

    EReportStatus(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }
}