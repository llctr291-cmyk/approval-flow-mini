package com.demo.approval.domain.model;

import com.demo.approval.common.BusinessException;
import com.demo.approval.common.ErrorCode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 流程定义（模板内容）。提交审批时整份序列化进实例的 snapshotJson，
 * 之后管理员再怎么改模板，已经在跑的单子都按自己的快照走完 —— 这就是"快照版本隔离"。
 */
public record FlowDefinition(String name, String startNodeId, List<FlowNodeDefinition> nodes) {

    public FlowDefinition {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public FlowNodeDefinition requireNode(String nodeId) {
        return nodes.stream()
                .filter(node -> node.nodeId().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_STATE, "流程定义中不存在节点: " + nodeId));
    }

    public boolean hasNode(String nodeId) {
        return nodes.stream().anyMatch(node -> node.nodeId().equals(nodeId));
    }

    /** 发布模板前做结构校验：起点存在、节点不重复、每个节点有审批人、出口指向存在的节点、最多一个默认出口。 */
    public void validate() {
        if (nodes.isEmpty()) {
            throw invalid("流程至少需要一个节点");
        }
        Set<String> ids = new HashSet<>();
        for (FlowNodeDefinition node : nodes) {
            if (node.nodeId() == null || node.nodeId().isBlank()) {
                throw invalid("节点 nodeId 不能为空");
            }
            if (!ids.add(node.nodeId())) {
                throw invalid("节点 nodeId 重复: " + node.nodeId());
            }
            if (node.approverIds().isEmpty()) {
                throw invalid("节点 " + node.nodeId() + " 没有配置审批人");
            }
        }
        if (!ids.contains(startNodeId)) {
            throw invalid("起始节点不存在: " + startNodeId);
        }
        for (FlowNodeDefinition node : nodes) {
            long defaultCount = node.transitions().stream().filter(FlowTransition::isDefault).count();
            if (defaultCount > 1) {
                throw invalid("节点 " + node.nodeId() + " 只能有一个默认出口");
            }
            for (FlowTransition transition : node.transitions()) {
                if (!transition.endsFlow() && !ids.contains(transition.targetNodeId())) {
                    throw invalid("节点 " + node.nodeId() + " 的出口指向不存在的节点: " + transition.targetNodeId());
                }
                if (node.nodeId().equals(transition.targetNodeId())) {
                    throw invalid("节点 " + node.nodeId() + " 不能指向自己");
                }
            }
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_DEFINITION, message);
    }
}
