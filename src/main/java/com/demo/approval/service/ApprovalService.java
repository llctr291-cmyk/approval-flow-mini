package com.demo.approval.service;

import com.demo.approval.common.AfterCommit;
import com.demo.approval.common.BusinessException;
import com.demo.approval.common.ErrorCode;
import com.demo.approval.domain.entity.ApprovalVote;
import com.demo.approval.domain.entity.FlowInstance;
import com.demo.approval.domain.model.FlowDefinition;
import com.demo.approval.domain.model.FlowNodeDefinition;
import com.demo.approval.domain.model.NodeDecision;
import com.demo.approval.domain.model.VoteAction;
import com.demo.approval.engine.ApproverResolver;
import com.demo.approval.engine.ConvergenceEvaluator;
import com.demo.approval.engine.FlowRoutingEngine;
import com.demo.approval.engine.NextState;
import com.demo.approval.repository.ApprovalVoteRepository;
import com.demo.approval.service.command.ApproveCommand;
import com.demo.approval.sync.FlowEventPublisher;
import com.demo.approval.web.dto.FlowInstanceView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审批主流程 —— 整个项目最核心的一个方法。
 *
 * <pre>
 * 用户点“同意”
 *   ① 幂等检查      同一个 Idempotency-Key 重复提交，直接返回上次结果
 *   ② 状态/版本检查  流程已结束 or 前端版本过期 → 拒绝
 *   ③ 权限检查      必须是当前节点（含加签）的审批人，且本轮没投过
 *   ④ 记录投票      唯一约束兜底，同一人同一轮只能一票
 *   ⑤ 收敛判断      会签/或签 → 过 / 拒 / 继续等
 *   ⑥ 动态路由      过了就按表单条件算下一站
 *   ⑦ CAS 写库      version + 1，并发只有一个人成功，失败整个事务回滚（含第④步的票）
 *   ⑧ 副作用        完成/撤销待办、发下一节点待办、扣减/释放预算
 *   ⑨ 写事件日志     seq 递增，提交后 WebSocket 推送 patch
 *   ⑩ 记录幂等结果
 * </pre>
 *
 * <p><b>为什么"只记一票"也要 CAS 加版本？</b>
 * 会签两人同时同意：如果不 CAS，A 只看到自己的票（PENDING），B 也只看到自己的票（PENDING），
 * 两个事务都提交后两票齐了，但没有任何一个事务做出"通过"的判断 —— 单子永远卡在这个节点。
 * 让每次投票都抢版本号，就把并发投票串行化了：输的那个重试时一定能看到赢家的票。</p>
 */
@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);

    private final FlowQueryService queryService;
    private final ApprovalVoteRepository voteRepository;
    private final ApproverResolver approverResolver;
    private final ConvergenceEvaluator convergenceEvaluator;
    private final FlowRoutingEngine routingEngine;
    private final FlowStateWriter stateWriter;
    private final TodoService todoService;
    private final BudgetService budgetService;
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final FlowEventPublisher eventPublisher;

    public ApprovalService(FlowQueryService queryService,
                           ApprovalVoteRepository voteRepository,
                           ApproverResolver approverResolver,
                           ConvergenceEvaluator convergenceEvaluator,
                           FlowRoutingEngine routingEngine,
                           FlowStateWriter stateWriter,
                           TodoService todoService,
                           BudgetService budgetService,
                           NotificationService notificationService,
                           IdempotencyService idempotencyService,
                           FlowEventPublisher eventPublisher) {
        this.queryService = queryService;
        this.voteRepository = voteRepository;
        this.approverResolver = approverResolver;
        this.convergenceEvaluator = convergenceEvaluator;
        this.routingEngine = routingEngine;
        this.stateWriter = stateWriter;
        this.todoService = todoService;
        this.budgetService = budgetService;
        this.notificationService = notificationService;
        this.idempotencyService = idempotencyService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public FlowInstanceView approve(ApproveCommand command) {
        // ① 幂等
        var replay = idempotencyService.findReplay(command.operatorId(), command.idempotencyKey(), FlowInstanceView.class);
        if (replay.isPresent()) {
            return replay.get();
        }

        // ② 状态 + 版本
        FlowInstance instance = queryService.requireInstance(command.instanceId());
        FlowStateWriter.ensureRunning(instance);
        FlowStateWriter.ensureVersion(instance, command.expectedVersion());

        FlowDefinition definition = queryService.definitionOf(instance);
        FlowNodeDefinition node = definition.requireNode(instance.getCurrentNodeId());
        int roundNo = instance.getRoundNo();

        // ③ 权限
        Set<String> approvers = approverResolver.resolve(instance, node);
        if (!approvers.contains(command.operatorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "用户 " + command.operatorId() + " 不是节点「" + node.name() + "」的审批人");
        }
        if (voteRepository.existsByInstanceIdAndNodeIdAndRoundNoAndApproverId(
                instance.getId(), node.nodeId(), roundNo, command.operatorId())) {
            throw new BusinessException(ErrorCode.ALREADY_VOTED, "你已经在本节点投过票了");
        }

        // ④ 记录投票
        voteRepository.save(new ApprovalVote(instance.getId(), node.nodeId(), roundNo,
                command.operatorId(), command.action(), command.comment()));

        // ⑤ 收敛判断（读到的票包含刚插入的这一票）
        Map<String, VoteAction> votes = queryService.votesOf(instance.getId(), node.nodeId(), roundNo);
        NodeDecision decision = convergenceEvaluator.evaluate(node.signMode(), approvers, votes);

        // ⑥ 动态路由 → 计算下一状态
        NextState next = switch (decision) {
            case PENDING -> NextState.stay(instance);
            case REJECTED -> NextState.reject(instance);
            case PASSED -> routingEngine.resolveNext(node, queryService.formOf(instance))
                    .map(nextNodeId -> NextState.moveTo(instance, nextNodeId))
                    .orElseGet(() -> NextState.finish(instance));
        };

        // ⑦ CAS 写库
        long newVersion = stateWriter.apply(instance.getId(), command.expectedVersion(), next);

        // ⑧ 副作用
        applySideEffects(instance, definition, node, next, command.operatorId());

        // ⑨ 事件日志 + 推送
        FlowInstanceView view = queryService.getView(instance.getId());
        eventPublisher.publish(instance.getId(), newVersion, next.eventType(), command.operatorId(), view.toPatch());

        // ⑩ 幂等结果
        idempotencyService.remember(command.operatorId(), command.idempotencyKey(), view);

        log.info("[审批] instance={} node={} round={} operator={} action={} decision={} version={}",
                instance.getId(), node.nodeId(), roundNo, command.operatorId(), command.action(), decision, newVersion);
        return view;
    }

    private void applySideEffects(FlowInstance before, FlowDefinition definition, FlowNodeDefinition node,
                                  NextState next, String operatorId) {
        String instanceId = before.getId();
        int roundNo = before.getRoundNo();
        todoService.complete(instanceId, node.nodeId(), roundNo, operatorId);

        switch (next.eventType()) {
            case VOTED -> {
                // 还在等其他人，什么都不用做
            }
            case NODE_PASSED -> {
                todoService.cancelRemainingOnNode(instanceId, node.nodeId(), roundNo);
                FlowNodeDefinition nextNode = definition.requireNode(next.nodeId());
                todoService.createForNode(instanceId, nextNode.nodeId(), roundNo, nextNode.approverIds());
                if (node.nodeId().equals(definition.startNodeId())) {
                    // 回退到起点时预算被补偿释放了，起点重新通过后再锁回来；正常首轮是 no-op
                    budgetService.relock(instanceId);
                }
                List<String> nextApprovers = nextNode.approverIds();
                AfterCommit.run(() -> safeNotify(nextApprovers, "你有一条新的审批待办：" + nextNode.name()));
            }
            case APPROVED -> {
                todoService.cancelRemainingOnNode(instanceId, node.nodeId(), roundNo);
                budgetService.consume(instanceId);
                AfterCommit.run(() -> safeNotify(List.of(before.getInitiatorId()), "你的申请已审批通过"));
            }
            case REJECTED -> {
                todoService.cancelRound(instanceId, roundNo);
                budgetService.release(instanceId);
                AfterCommit.run(() -> safeNotify(List.of(before.getInitiatorId()), "你的申请被驳回"));
            }
            default -> throw new IllegalStateException("审批不应产生事件类型: " + next.eventType());
        }
    }

    /** 通知失败不影响审批结果（已经提交了），只记日志。 */
    private void safeNotify(List<String> userIds, String message) {
        try {
            notificationService.notifyUsers(userIds, message);
        } catch (RuntimeException e) {
            log.warn("[通知] 发送失败 to={} error={}", userIds, e.getMessage());
        }
    }
}
