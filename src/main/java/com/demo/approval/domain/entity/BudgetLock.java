package com.demo.approval.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/** 预算占用：提交时锁定，审批通过时扣减，驳回/回退到起点时释放（补偿）。 */
@Entity
@Table(name = "budget_lock")
public class BudgetLock {

    public enum Status {
        LOCKED,
        CONSUMED,
        RELEASED
    }

    @Id
    @Column(name = "instance_id", length = 36)
    private String instanceId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BudgetLock() {
    }

    public BudgetLock(String instanceId, BigDecimal amount) {
        this.instanceId = instanceId;
        this.amount = amount;
        this.status = Status.LOCKED;
        this.updatedAt = Instant.now();
    }

    /** 幂等：只有 LOCKED 才能释放，重复调用不报错。返回是否真的发生了变化。 */
    public boolean release() {
        if (status != Status.LOCKED) {
            return false;
        }
        status = Status.RELEASED;
        updatedAt = Instant.now();
        return true;
    }

    public boolean consume() {
        if (status != Status.LOCKED) {
            return false;
        }
        status = Status.CONSUMED;
        updatedAt = Instant.now();
        return true;
    }

    /** 回退后重新提交时再次锁定。 */
    public boolean relock() {
        if (status != Status.RELEASED) {
            return false;
        }
        status = Status.LOCKED;
        updatedAt = Instant.now();
        return true;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
