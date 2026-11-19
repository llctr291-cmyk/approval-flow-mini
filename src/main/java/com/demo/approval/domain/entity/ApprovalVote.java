package com.demo.approval.domain.entity;

import com.demo.approval.domain.model.VoteAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 一张投票。唯一约束 (instance, node, round, approver) 是数据库层的最后一道防线：
 * 就算应用层的重复投票检查被并发绕过，同一个人同一轮也只能插入一票。
 */
@Entity
@Table(name = "approval_vote", uniqueConstraints = @UniqueConstraint(
        name = "uk_vote_once_per_round",
        columnNames = {"instance_id", "node_id", "round_no", "approver_id"}))
public class ApprovalVote {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VoteAction action;

    @Column(length = 500)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApprovalVote() {
    }

    public ApprovalVote(String instanceId, String nodeId, int roundNo, String approverId, VoteAction action, String comment) {
        this.instanceId = instanceId;
        this.nodeId = nodeId;
        this.roundNo = roundNo;
        this.approverId = approverId;
        this.action = action;
        this.comment = comment;
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

    public VoteAction getAction() {
        return action;
    }

    public String getComment() {
        return comment;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
