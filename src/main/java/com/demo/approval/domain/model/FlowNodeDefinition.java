package com.demo.approval.domain.model;

import java.util.List;

/** 流程中的一个审批节点。 */
public record FlowNodeDefinition(
        String nodeId,
        String name,
        SignMode signMode,
        List<String> approverIds,
        List<FlowTransition> transitions) {

    public FlowNodeDefinition {
        approverIds = approverIds == null ? List.of() : List.copyOf(approverIds);
        transitions = transitions == null ? List.of() : List.copyOf(transitions);
        signMode = signMode == null ? SignMode.ANY : signMode;
    }
}
