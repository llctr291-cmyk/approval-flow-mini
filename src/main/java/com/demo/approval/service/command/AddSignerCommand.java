package com.demo.approval.service.command;

public record AddSignerCommand(
        String instanceId,
        String operatorId,
        String newApproverId,
        long expectedVersion) {
}
