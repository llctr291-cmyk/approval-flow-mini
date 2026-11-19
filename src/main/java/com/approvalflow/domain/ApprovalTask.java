package com.approvalflow.domain;

public final class ApprovalTask {
    private final String id;
    private final String nodeId;
    private final String assignee;
    private TaskStatus status = TaskStatus.PENDING;

    public ApprovalTask(String id, String nodeId, String assignee) {
        this.id = id;
        this.nodeId = nodeId;
        this.assignee = assignee;
    }

    public String getId() { return id; }
    public String getNodeId() { return nodeId; }
    public String getAssignee() { return assignee; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
}
