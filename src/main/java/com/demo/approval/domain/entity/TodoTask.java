package com.demo.approval.domain.entity;

import com.demo.approval.domain.model.TodoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** 待办。唯一约束保证同一节点同一轮次不会给同一个人发两条待办（并发收敛时最常见的脏数据）。 */
@Entity
@Table(name = "todo_task",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_todo_once",
                columnNames = {"instance_id", "node_id", "round_no", "assignee_id"}),
        indexes = @Index(name = "idx_todo_assignee_status", columnList = "assignee_id,status"))
public class TodoTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false, length = 36)
    private String instanceId;

    @Column(name = "node_id", nullable = false, length = 64)
    private String nodeId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(name = "assignee_id", nullable = false, length = 64)
    private String assigneeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TodoStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TodoTask() {
    }

    public TodoTask(String instanceId, String nodeId, int roundNo, String assigneeId) {
        this.instanceId = instanceId;
        this.nodeId = nodeId;
        this.roundNo = roundNo;
        this.assigneeId = assigneeId;
        this.status = TodoStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
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

    public String getAssigneeId() {
        return assigneeId;
    }

    public TodoStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
