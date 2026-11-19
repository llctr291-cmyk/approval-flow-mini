package com.demo.approval.service;

import com.demo.approval.common.AfterCommit;
import com.demo.approval.common.BusinessException;
import com.demo.approval.common.ErrorCode;
import com.demo.approval.domain.entity.ExtraApprover;
import com.demo.approval.domain.entity.FlowInstance;
import com.demo.approval.domain.model.FlowDefinition;
import com.demo.approval.domain.model.FlowNodeDefinition;
import com.demo.approval.engine.ApproverResolver;
import com.demo.approval.engine.NextState;
import com.demo.approval.repository.ExtraApproverRepository;
import com.demo.approval.service.command.AddSignerCommand;
import com.demo.approval.sync.FlowEventPublisher;
import com.demo.approval.web.dto.FlowInstanceView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态加签：当前节点的审批人临时拉一个人进来。
 *
 * <p>加签同样走 CAS：否则可能出现"A 正在加签 C，B 同时投出最后一票让节点通过"，
 * 结果 C 被加进了一个已经结束的节点，收到一条永远处理不了的待办。</p>
 *
 * <p>会签节点加签后，需要新加的人也同意才能通过；或签节点加签相当于多一个可以拍板的人。</p>
 */
@Service
public class AddSignerService {

    private static final Logger log = LoggerFactory.getLogger(AddSignerService.class);

    private final FlowQueryService queryService;
    private final ApproverResolver approverResolver;
    private final ExtraApproverRepository extraApproverRepository;
    private final FlowStateWriter stateWriter;
    private final TodoService todoService;
    private final NotificationService notificationService;
    private final FlowEventPublisher eventPublisher;

    public AddSignerService(FlowQueryService queryService,
                            ApproverResolver approverResolver,
                            ExtraApproverRepository extraApproverRepository,
                            FlowStateWriter stateWriter,
                            TodoService todoService,
                            NotificationService notificationService,
                            FlowEventPublisher eventPublisher) {
        this.queryService = queryService;
        this.approverResolver = approverResolver;
        this.extraApproverRepository = extraApproverRepository;
        this.stateWriter = stateWriter;
        this.todoService = todoService;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public FlowInstanceView addSigner(AddSignerCommand command) {
        FlowInstance instance = queryService.requireInstance(command.instanceId());
        FlowStateWriter.ensureRunning(instance);
        FlowStateWriter.ensureVersion(instance, command.expectedVersion());

        FlowDefinition definition = queryService.definitionOf(instance);
        FlowNodeDefinition node = definition.requireNode(instance.getCurrentNodeId());
        Set<String> approvers = approverResolver.resolve(instance, node);

        if (!approvers.contains(command.operatorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有当前节点的审批人可以加签");
        }
        if (approvers.contains(command.newApproverId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, command.newApproverId() + " 已经是当前节点的审批人");
        }
        if (command.newApproverId().equals(instance.getInitiatorId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不能把发起人加签为审批人");
        }

        extraApproverRepository.save(new ExtraApprover(instance.getId(), node.nodeId(), instance.getRoundNo(),
                command.newApproverId(), command.operatorId()));

        long newVersion = stateWriter.apply(instance.getId(), command.expectedVersion(), NextState.signerAdded(instance));
        todoService.createForOne(instance.getId(), node.nodeId(), instance.getRoundNo(), command.newApproverId());

        FlowInstanceView view = queryService.getView(instance.getId());
        Map<String, Object> patch = new LinkedHashMap<>(view.toPatch());
        patch.put("addedSigner", Map.of("approverId", command.newApproverId(), "addedBy", command.operatorId()));
        eventPublisher.publish(instance.getId(), newVersion, NextState.signerAdded(instance).eventType(),
                command.operatorId(), patch);

        AfterCommit.run(() -> {
            try {
                notificationService.notifyUsers(List.of(command.newApproverId()),
                        command.operatorId() + " 邀请你参与审批：" + node.name());
            } catch (RuntimeException e) {
                log.warn("[通知] 加签通知失败 to={} error={}", command.newApproverId(), e.getMessage());
            }
        });

        log.info("[加签] instance={} node={} round={} by={} add={} version={}",
                instance.getId(), node.nodeId(), instance.getRoundNo(), command.operatorId(),
                command.newApproverId(), newVersion);
        return view;
    }
}
