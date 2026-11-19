package com.demo.approval.service;

import com.demo.approval.common.BusinessException;
import com.demo.approval.common.ErrorCode;
import com.demo.approval.common.VisitedPath;
import com.demo.approval.compensation.CompensationExecutor;
import com.demo.approval.compensation.CompensationPlanner;
import com.demo.approval.domain.entity.CompensationTask;
import com.demo.approval.domain.entity.FlowInstance;
import com.demo.approval.domain.model.FlowDefinition;
import com.demo.approval.domain.model.FlowNodeDefinition;
import com.demo.approval.engine.ApproverResolver;
import com.demo.approval.engine.NextState;
import com.demo.approval.repository.CompensationTaskRepository;
import com.demo.approval.service.command.RollbackCommand;
import com.demo.approval.sync.FlowEventPublisher;
import com.demo.approval.web.dto.FlowInstanceView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨节点回退 + 补偿。
 *
 * <pre>
 * 例：路径 leader → manager → boss，老板打回到 leader
 *   1. CAS：currentNode = leader，round + 1，路径截断为 [leader]
 *   2. 正向动作（同事务）：给 leader 节点的人发新一轮待办
 *   3. 补偿任务（同事务落 outbox，提交后异步执行，失败重试）：
 *        - 撤销旧轮次所有未处理待办（manager、boss 的）
 *        - 回到起点 → 释放预算占用
 *        - 通知被跳过的审批人"单据已退回，无需处理"
 * </pre>
 *
 * <p>为什么补偿不直接在事务里同步做？补偿动作往往是调外部系统（IM、预算中心），
 * 外部调用失败不应该让"回退"本身失败，也不能在事务里长时间等待外部响应。
 * 所以采用 Outbox：状态变更和"要做哪些补偿"原子落库，执行交给可重试的异步任务。</p>
 */
@Service
public class RollbackService {

    private static final Logger log = LoggerFactory.getLogger(RollbackService.class);

    private final FlowQueryService queryService;
    private final ApproverResolver approverResolver;
    private final FlowStateWriter stateWriter;
    private final TodoService todoService;
    private final CompensationPlanner compensationPlanner;
    private final CompensationTaskRepository compensationTaskRepository;
    private final CompensationExecutor compensationExecutor;
    private final FlowEventPublisher eventPublisher;

    public RollbackService(FlowQueryService queryService,
                           ApproverResolver approverResolver,
                           FlowStateWriter stateWriter,
                           TodoService todoService,
                           CompensationPlanner compensationPlanner,
                           CompensationTaskRepository compensationTaskRepository,
                           CompensationExecutor compensationExecutor,
                           FlowEventPublisher eventPublisher) {
        this.queryService = queryService;
        this.approverResolver = approverResolver;
        this.stateWriter = stateWriter;
        this.todoService = todoService;
        this.compensationPlanner = compensationPlanner;
        this.compensationTaskRepository = compensationTaskRepository;
        this.compensationExecutor = compensationExecutor;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public FlowInstanceView rollback(RollbackCommand command) {
        FlowInstance instance = queryService.requireInstance(command.instanceId());
        FlowStateWriter.ensureRunning(instance);
        FlowStateWriter.ensureVersion(instance, command.expectedVersion());

        FlowDefinition definition = queryService.definitionOf(instance);
        FlowNodeDefinition currentNode = definition.requireNode(instance.getCurrentNodeId());
        List<String> currentApprovers = new ArrayList<>(approverResolver.resolve(instance, currentNode));
        if (!currentApprovers.contains(command.operatorId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有当前节点的审批人可以回退");
        }

        List<String> path = VisitedPath.parse(instance.getVisitedPath());
        int targetIndex = path.lastIndexOf(command.targetNodeId());
        if (targetIndex < 0 || targetIndex == path.size() - 1) {
            throw new BusinessException(ErrorCode.INVALID_STATE,
                    "只能回退到已经走过的上游节点，当前路径: " + path);
        }
        List<String> skippedNodes = List.copyOf(path.subList(targetIndex + 1, path.size()));
        int oldRound = instance.getRoundNo();
        boolean backToStart = command.targetNodeId().equals(definition.startNodeId());

        // 1. CAS
        NextState next = NextState.rollbackTo(instance, command.targetNodeId());
        long newVersion = stateWriter.apply(instance.getId(), command.expectedVersion(), next);

        // 2. 正向动作：目标节点新一轮待办
        FlowNodeDefinition targetNode = definition.requireNode(command.targetNodeId());
        todoService.createForNode(instance.getId(), targetNode.nodeId(), next.roundNo(), targetNode.approverIds());

        // 3. 补偿任务落库（和 CAS 同一个事务：要么都成功，要么都没发生）
        List<CompensationTask> tasks = compensationPlanner.planForRollback(
                instance.getId(), oldRound, skippedNodes, backToStart, currentApprovers, command.reason());
        List<Long> taskIds = compensationTaskRepository.saveAll(tasks).stream()
                .map(CompensationTask::getId)
                .toList();
        compensationExecutor.runAfterCommit(taskIds);

        // 4. 事件
        FlowInstanceView view = queryService.getView(instance.getId());
        Map<String, Object> rollbackInfo = new LinkedHashMap<>();
        rollbackInfo.put("from", currentNode.nodeId());
        rollbackInfo.put("to", targetNode.nodeId());
        rollbackInfo.put("skippedNodes", skippedNodes);
        rollbackInfo.put("reason", command.reason());
        Map<String, Object> patch = new LinkedHashMap<>(view.toPatch());
        patch.put("rollback", rollbackInfo);
        eventPublisher.publish(instance.getId(), newVersion, next.eventType(), command.operatorId(), patch);

        log.info("[回退] instance={} {} -> {} round {}->{} skipped={} compensations={} version={}",
                instance.getId(), currentNode.nodeId(), targetNode.nodeId(), oldRound, next.roundNo(),
                skippedNodes, taskIds.size(), newVersion);
        return view;
    }
}
