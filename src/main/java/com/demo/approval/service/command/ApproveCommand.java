package com.demo.approval.service.command;

import com.demo.approval.domain.model.VoteAction;

/**
 * 审批命令。
 *
 * @param idempotencyKey 可空；为空时不做幂等保护（例如内部批处理调用）
 */
public record ApproveCommand(
        String instanceId,
        String operatorId,
        VoteAction action,
        String comment,
        long expectedVersion,
        String idempotencyKey) {
}
