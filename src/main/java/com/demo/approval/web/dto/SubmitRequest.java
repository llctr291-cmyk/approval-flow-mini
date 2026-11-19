package com.demo.approval.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record SubmitRequest(
        @NotBlank String templateKey,
        @NotNull Map<String, Object> form) {
}
