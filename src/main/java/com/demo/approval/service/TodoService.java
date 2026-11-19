package com.demo.approval.service;

import com.demo.approval.domain.entity.TodoTask;
import com.demo.approval.domain.model.TodoStatus;
import com.demo.approval.repository.TodoTaskRepository;
import com.demo.approval.web.dto.TodoView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** 待办管理。所有写操作都带"从哪个状态变过来"的条件，重复执行是幂等的，补偿重试时安全。 */
@Service
public class TodoService {

    private static final Logger log = LoggerFactory.getLogger(TodoService.class);

    private final TodoTaskRepository repository;

    public TodoService(TodoTaskRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public int createForNode(String instanceId, String nodeId, int roundNo, Collection<String> assignees) {
        int created = 0;
        for (String assignee : assignees) {
            if (repository.existsByInstanceIdAndNodeIdAndRoundNoAndAssigneeId(instanceId, nodeId, roundNo, assignee)) {
                continue;
            }
            repository.save(new TodoTask(instanceId, nodeId, roundNo, assignee));
            created++;
        }
        log.info("[待办] 创建 instance={} node={} round={} count={}", instanceId, nodeId, roundNo, created);
        return created;
    }

    @Transactional
    public void createForOne(String instanceId, String nodeId, int roundNo, String assignee) {
        createForNode(instanceId, nodeId, roundNo, List.of(assignee));
    }

    @Transactional
    public void complete(String instanceId, String nodeId, int roundNo, String assignee) {
        repository.transitionOne(instanceId, nodeId, roundNo, assignee,
                TodoStatus.PENDING, TodoStatus.DONE, Instant.now());
    }

    /** 节点已收敛（例如或签有人同意了），其他人的待办作废。 */
    @Transactional
    public int cancelRemainingOnNode(String instanceId, String nodeId, int roundNo) {
        int cancelled = repository.transitionNode(instanceId, nodeId, roundNo,
                TodoStatus.PENDING, TodoStatus.CANCELLED, Instant.now());
        if (cancelled > 0) {
            log.info("[待办] 节点收敛，撤销剩余待办 instance={} node={} count={}", instanceId, nodeId, cancelled);
        }
        return cancelled;
    }

    /** 撤销某一轮次的全部待办（驳回、回退补偿）。 */
    @Transactional
    public int cancelRound(String instanceId, int roundNo) {
        int cancelled = repository.transitionRound(instanceId, roundNo,
                TodoStatus.PENDING, TodoStatus.CANCELLED, Instant.now());
        log.info("[待办] 撤销轮次待办 instance={} round={} count={}", instanceId, roundNo, cancelled);
        return cancelled;
    }

    @Transactional(readOnly = true)
    public List<TodoView> listPending(String assigneeId) {
        return repository.findByAssigneeIdAndStatusOrderByCreatedAtDesc(assigneeId, TodoStatus.PENDING).stream()
                .map(TodoView::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TodoView> listByInstance(String instanceId) {
        return repository.findByInstanceIdOrderByIdAsc(instanceId).stream()
                .map(TodoView::from)
                .toList();
    }
}
