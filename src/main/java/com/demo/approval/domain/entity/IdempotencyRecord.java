package com.demo.approval.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 幂等记录：key = operatorId + ":" + Idempotency-Key。
 * 第一次处理成功后把响应存下来，之后同一个 key 的重试直接返回这份响应，不再走业务逻辑。
 */
@Entity
@Table(name = "idempotency_record")
public class IdempotencyRecord {

    @Id
    @Column(name = "record_key", length = 200)
    private String recordKey;

    @Column(name = "operator_id", nullable = false, length = 64)
    private String operatorId;

    @Lob
    @Column(name = "response_json", nullable = false)
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String recordKey, String operatorId, String responseJson) {
        this.recordKey = recordKey;
        this.operatorId = operatorId;
        this.responseJson = responseJson;
        this.createdAt = Instant.now();
    }

    public String getRecordKey() {
        return recordKey;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
