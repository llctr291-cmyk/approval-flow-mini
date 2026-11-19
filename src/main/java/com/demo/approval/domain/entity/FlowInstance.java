package com.demo.approval.domain.entity;

import com.demo.approval.domain.model.InstanceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 流程实例（一张具体的单子）。
 *
 * <p>注意 version 字段<b>没有</b>用 JPA 的 {@code @Version}：
 * 我们要求客户端显式带上 expectedVersion，并通过
 * {@code FlowInstanceRepository#compareAndSet} 手写 CAS 更新，
 * 这样"前端看到的版本"和"数据库里的版本"是同一个东西，可以直接用于推送去重。</p>
 */
@Entity
@Table(name = "flow_instance", indexes = {
        @Index(name = "idx_instance_initiator", columnList = "initiator_id"),
        @Index(name = "idx_instance_status", columnList = "status")
})
public class FlowInstance {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "template_key", nullable = false, length = 64)
    private String templateKey;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;

    /** 提交时的流程定义快照。 */
    @Lob
    @Column(name = "snapshot_json", nullable = false)
    private String snapshotJson;

    @Lob
    @Column(name = "form_json", nullable = false)
    private String formJson;

    @Column(name = "initiator_id", nullable = false, length = 64)
    private String initiatorId;

    /** 流程结束后为 null。 */
    @Column(name = "current_node_id", length = 64)
    private String currentNodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InstanceStatus status;

    /** 审批轮次：每次回退 +1，投票和待办都按 (node, round) 隔离，回退后旧票自然失效。 */
    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(name = "visited_path", nullable = false, length = 2000)
    private String visitedPath;

    /** 单调递增版本号：每次状态变化 +1。 */
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FlowInstance() {
    }

    public static FlowInstance start(String id, FlowTemplate template, String formJson, String initiatorId, String startNodeId) {
        FlowInstance instance = new FlowInstance();
        instance.id = id;
        instance.templateKey = template.getTemplateKey();
        instance.templateVersion = template.getTemplateVersion();
        instance.snapshotJson = template.getDefinitionJson();
        instance.formJson = formJson;
        instance.initiatorId = initiatorId;
        instance.currentNodeId = startNodeId;
        instance.status = InstanceStatus.RUNNING;
        instance.roundNo = 1;
        instance.visitedPath = startNodeId;
        instance.version = 1L;
        instance.createdAt = Instant.now();
        instance.updatedAt = instance.createdAt;
        return instance;
    }

    public String getId() {
        return id;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public int getTemplateVersion() {
        return templateVersion;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public String getFormJson() {
        return formJson;
    }

    public String getInitiatorId() {
        return initiatorId;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public InstanceStatus getStatus() {
        return status;
    }

    public int getRoundNo() {
        return roundNo;
    }

    public String getVisitedPath() {
        return visitedPath;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
