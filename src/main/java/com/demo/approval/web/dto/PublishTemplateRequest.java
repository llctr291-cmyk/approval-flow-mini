package com.demo.approval.web.dto;

import com.demo.approval.domain.model.FlowDefinition;
import jakarta.validation.constraints.NotNull;

public record PublishTemplateRequest(@NotNull FlowDefinition definition) {
}
