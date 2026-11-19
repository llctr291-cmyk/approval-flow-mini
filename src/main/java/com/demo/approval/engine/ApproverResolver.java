package com.demo.approval.engine;

import com.demo.approval.domain.entity.ExtraApprover;
import com.demo.approval.domain.entity.FlowInstance;
import com.demo.approval.domain.model.FlowNodeDefinition;
import com.demo.approval.repository.ExtraApproverRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** 节点实际审批人 = 模板快照里配置的人 + 本轮动态加签的人（去重、保持顺序）。 */
@Component
public class ApproverResolver {

    private final ExtraApproverRepository extraApproverRepository;

    public ApproverResolver(ExtraApproverRepository extraApproverRepository) {
        this.extraApproverRepository = extraApproverRepository;
    }

    public Set<String> resolve(FlowInstance instance, FlowNodeDefinition node) {
        return resolve(instance.getId(), node, instance.getRoundNo());
    }

    public Set<String> resolve(String instanceId, FlowNodeDefinition node, int roundNo) {
        Set<String> approvers = new LinkedHashSet<>(node.approverIds());
        extraApproverRepository.findByInstanceIdAndNodeIdAndRoundNo(instanceId, node.nodeId(), roundNo)
                .stream()
                .map(ExtraApprover::getApproverId)
                .forEach(approvers::add);
        return approvers;
    }
}
