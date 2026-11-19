package com.demo.approval.web.dto;

import com.demo.approval.domain.model.FlowDefinition;

import java.time.Instant;

public record TemplateView(String templateKey, int templateVersion, FlowDefinition definition,
                           String publishedBy, Instant createdAt) {
}
