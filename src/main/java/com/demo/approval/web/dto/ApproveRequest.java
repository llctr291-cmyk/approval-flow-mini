package com.demo.approval.web.dto;

import com.demo.approval.domain.model.VoteAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param expectedVersion 前端当前看到的版本号，和库里不一致直接 409，防止"基于旧数据做决定"
 */
public record ApproveRequest(
        @NotNull VoteAction action,
        @Size(max = 500) String comment,
        @NotNull @Positive Long expectedVersion) {
}
