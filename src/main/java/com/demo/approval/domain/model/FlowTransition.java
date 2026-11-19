package com.demo.approval.domain.model;

/**
 * 节点出口。condition 为空表示默认出口；targetNodeId 为空表示流程结束。
 * condition 使用受限 SpEL，例如 {@code #form['amount'] > 5000}。
 */
public record FlowTransition(String condition, String targetNodeId) {

    public boolean isDefault() {
        return condition == null || condition.isBlank();
    }

    public boolean endsFlow() {
        return targetNodeId == null || targetNodeId.isBlank();
    }
}
