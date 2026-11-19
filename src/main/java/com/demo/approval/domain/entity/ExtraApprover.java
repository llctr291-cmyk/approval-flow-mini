package com.demo.approval.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** 动态加签：审批过程中临时拉进当前节点的审批人，只在 (node, round) 内有效。 */
@Entity
@Table(name = "extra_approver", uniqueConstraints = @UniqueConstraint(
        name = "uk_extra_approver",
        columnNames = {"instance_id", "node_id", "round_no", "approver_id"}))
public class ExtraApprover {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false, length = 36)
    private String instanceId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(name = "approver_id", nullable = false, length = 64)
    private String approverId;

    @Column(name = "added_by", nullable = false, length = 64)
    private String addedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ExtraApprover() {
    }

    public ExtraApprover(String instanceId, String nodeId, int roundNo, String approverId, String addedBy) {
        this.instanceId = instanceId;
        this.nodeId = nodeId;
        this.roundNo = roundNo;
        this.approverId = approverId;
        this.addedBy = addedBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getRoundNo() {
        return roundNo;
    }

    public String getApproverId() {
        return approverId;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
