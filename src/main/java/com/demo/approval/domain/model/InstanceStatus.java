package com.demo.approval.domain.model;

public enum InstanceStatus {
    RUNNING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isFinished() {
        return this != RUNNING;
    }
}
