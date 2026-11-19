package com.demo.approval.service;

import com.demo.approval.common.BusinessException;
import com.demo.approval.common.JsonCodec;
import com.demo.approval.common.VisitedPath;
import com.demo.approval.domain.entity.ApprovalVote;
import com.demo.approval.domain.entity.FlowInstance;
import com.demo.approval.domain.model.FlowDefinition;
import com.demo.approval.domain.model.FlowNodeDefinition;
import com.demo.approval.domain.model.VoteAction;
import com.demo.approval.engine.ApproverResolver;
import com.demo.approval.engine.ConvergenceEvaluator;
import com.demo.approval.repository.ApprovalVoteRepository;
import com.demo.approval.repository.FlowInstanceRepository;
import com.demo.approval.web.dto.FlowInstanceView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 读侧：组装实例视图，同时给写侧提供"加载实例 / 解析快照 / 读票"等公共方法。 */
@Service
@Transactional(readOnly = true)
public class FlowQueryService {

    private final FlowInstanceRepository instanceRepository;
    private final ApprovalVoteRepository voteRepository;
    private final ApproverResolver approverResolver;
    private final ConvergenceEvaluator convergenceEvaluator;
    private final JsonCodec jsonCodec;

    public FlowQueryService(FlowInstanceRepository instanceRepository,
                            ApprovalVoteRepository voteRepository,
                            ApproverResolver approverResolver,
                            ConvergenceEvaluator convergenceEvaluator,
                            JsonCodec jsonCodec) {
        this.instanceRepository = instanceRepository;
        this.voteRepository = voteRepository;
        this.approverResolver = approverResolver;
        this.convergenceEvaluator = convergenceEvaluator;
        this.jsonCodec = jsonCodec;
    }

    public FlowInstance requireInstance(String instanceId) {
        return instanceRepository.findById(instanceId)
                .orElseThrow(() -> BusinessException.notFound("流程实例", instanceId));
    }

    /** 读的是实例自己的快照，而不是模板表的最新版本。 */
    public FlowDefinition definitionOf(FlowInstance instance) {
        return jsonCodec.read(instance.getSnapshotJson(), FlowDefinition.class);
    }

    public Map<String, Object> formOf(FlowInstance instance) {
        return jsonCodec.readMap(instance.getFormJson());
    }

    public Map<String, VoteAction> votesOf(String instanceId, String nodeId, int roundNo) {
        return voteRepository.findByInstanceIdAndNodeIdAndRoundNo(instanceId, nodeId, roundNo).stream()
                .collect(Collectors.toMap(
                        ApprovalVote::getApproverId,
                        ApprovalVote::getAction,
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    public FlowInstanceView getView(String instanceId) {
        return toView(requireInstance(instanceId));
    }

    public List<FlowInstanceView> listByInitiator(String initiatorId) {
        return instanceRepository.findByInitiatorIdOrderByCreatedAtDesc(initiatorId).stream()
                .map(this::toView)
                .toList();
    }

    public FlowInstanceView toView(FlowInstance instance) {
        FlowDefinition definition = definitionOf(instance);
        FlowNodeDefinition node = instance.getCurrentNodeId() == null
                ? null
                : definition.requireNode(instance.getCurrentNodeId());

        List<String> approvers = node == null
                ? List.of()
                : new ArrayList<>(approverResolver.resolve(instance, node));
        Map<String, VoteAction> votes = node == null
                ? Map.of()
                : votesOf(instance.getId(), node.nodeId(), instance.getRoundNo());
        List<String> waitingFor = node == null
                ? List.of()
                : convergenceEvaluator.waitingFor(approvers, votes);

        return new FlowInstanceView(
                instance.getId(),
                instance.getTemplateKey(),
                instance.getTemplateVersion(),
                instance.getStatus(),
                instance.getCurrentNodeId(),
                node == null ? null : node.name(),
                node == null ? null : node.signMode(),
                instance.getRoundNo(),
                instance.getVersion(),
                approvers,
                votes,
                waitingFor,
                VisitedPath.parse(instance.getVisitedPath()),
                instance.getInitiatorId(),
                formOf(instance),
                instance.getUpdatedAt());
    }
}
