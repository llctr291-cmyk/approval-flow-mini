package com.demo.approval.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/**
 * 补偿任务（Outbox 模式）。回退时和状态变更在同一事务里落库，
 * 提交后立即异步执行；失败按指数退避重试，超过最大次数进入 DEAD（人工处理队列）。
 */
@Entity
@Table(name = "compensation_task", indexes = @Index(name = "idx_comp_status_next", columnList = "status,next_run_at"))
public class CompensationTask {

    public enum Status {
        PENDING,
        RUNNING,
        DONE,
        DEAD
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false, length = 36)
    private String instanceId;

    @Column(name = "step_name", nullable = false, length = 64)
    private String stepName;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "next_run_at", nullable = false)
    private Instant nextRunAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CompensationTask() {
    }

    public CompensationTask(String instanceId, String stepName, String payloadJson) {
        this.instanceId = instanceId;
        this.stepName = stepName;
        this.payloadJson = payloadJson;
        this.status = Status.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.nextRunAt = this.createdAt;
    }

    public void markDone() {
        status = Status.DONE;
        lastError = null;
        updatedAt = Instant.now();
    }

    /** 失败：未超过次数则按 2^attempts 秒退避，否则进入 DEAD。 */
    public void markFailed(String error, int maxAttempts) {
        lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 1000));
        updatedAt = Instant.now();
        if (attempts >= maxAttempts) {
            status = Status.DEAD;
            return;
        }
        status = Status.PENDING;
        long backoffSeconds = Math.min(300, 1L << Math.min(attempts, 8));
        nextRunAt = updatedAt.plus(Duration.ofSeconds(backoffSeconds));
    }

    /** 人工处理 DEAD 任务后重新投递：清零次数，立即可执行。 */
    public void resetForRetry() {
        status = Status.PENDING;
        attempts = 0;
        lastError = null;
        updatedAt = Instant.now();
        nextRunAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getStepName() {
        return stepName;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
