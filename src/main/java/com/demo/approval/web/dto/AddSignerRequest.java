package com.demo.approval.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddSignerRequest(
        @NotBlank String approverId,
        @NotNull @Positive Long expectedVersion) {
}
