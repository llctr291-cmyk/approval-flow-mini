package com.demo.approval.domain.entity;

import com.demo.approval.domain.model.FlowEventType;
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

import java.time.Instant;

/**
 * 事件日志（Outbox）。和状态变更在同一个事务里写入：
 * <ul>
 *   <li>seq：全局自增，客户端用它当游标做断线补拉；</li>
 *   <li>instanceVersion：实例版本号，客户端用它丢弃乱序的旧事件；</li>
 *   <li>patchJson：只包含变化字段的增量。</li>
 * </ul>
 */
@Entity
@Table(name = "flow_event", indexes = {
        @Index(name = "idx_event_instance_seq", columnList = "instance_id,seq"),
        @Index(name = "idx_event_created", columnList = "created_at")
})
public class FlowEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seq;

    @Column(name = "instance_id", nullable = false, length = 36)
    private String instanceId;

    @Column(name = "instance_version", nullable = false)
    private long instanceVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private FlowEventType type;

    @Column(name = "operator_id", length = 64)
    private String operatorId;

    @Lob
    @Column(name = "patch_json", nullable = false)
    private String patchJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FlowEvent() {
    }

    public FlowEvent(String instanceId, long instanceVersion, FlowEventType type, String operatorId, String patchJson) {
        this.instanceId = instanceId;
        this.instanceVersion = instanceVersion;
        this.type = type;
        this.operatorId = operatorId;
        this.patchJson = patchJson;
        this.createdAt = Instant.now();
    }

    public Long getSeq() {
        return seq;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public long getInstanceVersion() {
        return instanceVersion;
    }

    public FlowEventType getType() {
        return type;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getPatchJson() {
        return patchJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
