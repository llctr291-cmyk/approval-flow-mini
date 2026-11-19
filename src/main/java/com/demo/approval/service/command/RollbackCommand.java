package com.demo.approval.service.command;

public record RollbackCommand(
        String instanceId,
        String operatorId,
        String targetNodeId,
        String reason,
        long expectedVersion) {
}
