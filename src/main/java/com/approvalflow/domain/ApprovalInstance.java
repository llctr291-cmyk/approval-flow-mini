package com.approvalflow.domain;

import java.util.ArrayList;
import java.util.List;

public final class ApprovalInstance {
    private final String id;
    private final String applicant;
    private final List<ApprovalNode> nodes;
    private final List<ApprovalTask> tasks = new ArrayList<>();
    private ApprovalStatus status = ApprovalStatus.RUNNING;
    private int currentNodeIndex;
    private long version;

    public ApprovalInstance(String id, String applicant, List<ApprovalNode> nodes) {
        this.id = id;
        this.applicant = applicant;
        this.nodes = nodes.stream().map(ApprovalNode::copyForInstance).toList();
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("at least one approval node is required");
        }
        for (ApprovalNode node : this.nodes) {
            for (String assignee : node.assignees()) {
                tasks.add(new ApprovalTask(id + "-task-" + (tasks.size() + 1), node.id(), assignee));
            }
        }
    }

    public String getId() { return id; }
    public String getApplicant() { return applicant; }
    public List<ApprovalNode> getNodes() { return List.copyOf(nodes); }
    public List<ApprovalTask> getTasks() { return List.copyOf(tasks); }
    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }
    public int getCurrentNodeIndex() { return currentNodeIndex; }
    public void setCurrentNodeIndex(int currentNodeIndex) { this.currentNodeIndex = currentNodeIndex; }
    public ApprovalNode getCurrentNode() { return nodes.get(currentNodeIndex); }
    public long getVersion() { return version; }
    public long incrementVersion() { return ++version; }

    public ApprovalTask addTask(String id, String nodeId, String assignee) {
        ApprovalTask task = new ApprovalTask(id, nodeId, assignee);
        tasks.add(task);
        return task;
    }
}
