package com.approvalflow.domain;

import java.util.List;

public record ApprovalNode(String id, CounterMode mode, List<String> assignees) {
    public ApprovalNode {
        assignees = assignees == null ? List.of() : List.copyOf(assignees);
    }

    public ApprovalNode copyForInstance() {
        return new ApprovalNode(id, mode, List.copyOf(assignees));
    }
}
