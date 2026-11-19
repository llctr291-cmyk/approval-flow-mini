package com.demo.approval.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record RollbackRequest(
        @NotBlank String targetNodeId,
        @Size(max = 500) String reason,
        @NotNull @Positive Long expectedVersion) {
}
