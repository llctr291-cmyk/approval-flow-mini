package com.demo.approval.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** 流程模板。每次发布生成新版本，旧版本保留（已在跑的实例引用的是快照，不依赖这里）。 */
@Entity
@Table(name = "flow_template", uniqueConstraints = @UniqueConstraint(
        name = "uk_template_key_version", columnNames = {"template_key", "template_version"}))
public class FlowTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_key", nullable = false, length = 64)
    private String templateKey;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;

    @Lob
    @Column(name = "definition_json", nullable = false)
    private String definitionJson;

    @Column(name = "published_by", length = 64)
    private String publishedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected FlowTemplate() {
    }

    public FlowTemplate(String templateKey, int templateVersion, String definitionJson, String publishedBy) {
        this.templateKey = templateKey;
        this.templateVersion = templateVersion;
        this.definitionJson = definitionJson;
        this.publishedBy = publishedBy;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public int getTemplateVersion() {
        return templateVersion;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }

    public String getPublishedBy() {
        return publishedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
