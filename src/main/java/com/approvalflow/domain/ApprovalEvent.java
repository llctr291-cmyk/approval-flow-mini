package com.approvalflow.domain;

public record ApprovalEvent(long cursor, String instanceId, String type, String payload) {
}
