package com.approvalflow.service;

import com.approvalflow.domain.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ApprovalEngine {
    private final Map<String, ApprovalInstance> instances = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> idempotency = new ConcurrentHashMap<>();
    private final Map<String, List<ApprovalEvent>> events = new ConcurrentHashMap<>();
    private final AtomicLong eventCursor = new AtomicLong();
    private final AtomicLong sequence = new AtomicLong();

    public ApprovalInstance create(String applicant, List<ApprovalNode> nodes) {
        String id = "approval-" + UUID.randomUUID();
        ApprovalInstance instance = new ApprovalInstance(id, applicant, nodes);
        instances.put(id, instance);
        events.put(id, Collections.synchronizedList(new ArrayList<>()));
        append(instance, "CREATED", applicant);
        return instance;
    }

    public ApprovalInstance act(String instanceId, String taskId, Decision decision,
                                String operator, String idempotencyKey) {
        return executeOnce(instanceId, idempotencyKey, "act:" + taskId + ":" + decision,
                () -> actUncached(instanceId, taskId, decision, operator));
    }

    private ApprovalInstance actUncached(String instanceId, String taskId, Decision decision, String operator) {
        ApprovalInstance instance = requireInstance(instanceId);
        synchronized (instance) {
            ApprovalTask task = instance.getTasks().stream()
                    .filter(t -> t.getId().equals(taskId))
                    .findFirst()
                    .orElseThrow(() -> new NoSuchElementException("task not found: " + taskId));
            if (task.getStatus() != TaskStatus.PENDING) {
                return instance;
            }
            if (instance.getStatus() != ApprovalStatus.RUNNING) {
                throw new IllegalStateException("approval instance is not running");
            }

            task.setStatus(decision == Decision.APPROVE ? TaskStatus.APPROVED : TaskStatus.REJECTED);
            instance.incrementVersion();
            append(instance, "TASK_" + decision, task.getNodeId() + ":" + operator);

            if (decision == Decision.REJECT) {
                ApprovalNode node = instance.getNodes().stream()
                        .filter(n -> n.id().equals(task.getNodeId()))
                        .findFirst()
                        .orElseThrow();
                boolean converged = node.mode() == CounterMode.ALL
                        || instance.getTasks().stream()
                        .filter(t -> t.getNodeId().equals(node.id()))
                        .noneMatch(t -> t.getStatus() == TaskStatus.PENDING
                                || t.getStatus() == TaskStatus.APPROVED);
                if (converged) {
                    instance.setStatus(ApprovalStatus.REJECTED);
                    append(instance, "REJECTED", task.getNodeId());
                    cancelPendingTasks(instance);
                }
            } else {
                ApprovalNode node = instance.getNodes().stream()
                        .filter(n -> n.id().equals(task.getNodeId()))
                        .findFirst()
                        .orElseThrow();
                boolean converged = node.mode() == CounterMode.ANY
                        || instance.getTasks().stream()
                        .filter(t -> t.getNodeId().equals(node.id()))
                        .allMatch(t -> t.getStatus() == TaskStatus.APPROVED);
                if (converged) {
                    advance(instance);
                }
            }
            return instance;
        }
    }

    public ApprovalInstance addSign(String instanceId, String nodeId, String assignee,
                                    String idempotencyKey) {
        return executeOnce(instanceId, idempotencyKey, "addSign:" + nodeId + ":" + assignee,
                () -> {
                    ApprovalInstance instance = requireInstance(instanceId);
                    synchronized (instance) {
                        if (instance.getStatus() != ApprovalStatus.RUNNING) {
                            throw new IllegalStateException("approval instance is not running");
                        }
                        boolean nodeExists = instance.getNodes().stream().anyMatch(n -> n.id().equals(nodeId));
                        if (!nodeExists) {
                            throw new NoSuchElementException("node not found: " + nodeId);
                        }
                        boolean duplicate = instance.getTasks().stream()
                                .anyMatch(t -> t.getNodeId().equals(nodeId)
                                        && t.getAssignee().equals(assignee)
                                        && t.getStatus() == TaskStatus.PENDING);
                        if (!duplicate) {
                            String taskId = instanceId + "-task-" + (instance.getTasks().size() + 1);
                            ApprovalTask task = instance.addTask(taskId, nodeId, assignee);
                            instance.incrementVersion();
                            append(instance, "ADDED_SIGN", nodeId + ":" + assignee);
                        }
                        return instance;
                    }
                });
    }

    public ApprovalInstance rollback(String instanceId, String targetNodeId, String reason,
                                     long expectedVersion, String idempotencyKey) {
        return executeOnce(instanceId, idempotencyKey, "rollback:" + targetNodeId + ":" + expectedVersion,
                () -> {
                    ApprovalInstance instance = requireInstance(instanceId);
                    synchronized (instance) {
                        if (instance.getVersion() != expectedVersion) {
                            throw new CasConflictException(instanceId, expectedVersion, instance.getVersion());
                        }
                        int targetIndex = -1;
                        for (int i = 0; i < instance.getNodes().size(); i++) {
                            if (instance.getNodes().get(i).id().equals(targetNodeId)) {
                                targetIndex = i;
                                break;
                            }
                        }
                        if (targetIndex < 0) {
                            throw new NoSuchElementException("node not found: " + targetNodeId);
                        }
                        if (instance.getStatus() != ApprovalStatus.RUNNING) {
                            instance.setStatus(ApprovalStatus.RUNNING);
                        }
                        for (ApprovalTask task : instance.getTasks()) {
                            if (task.getStatus() == TaskStatus.PENDING) {
                                task.setStatus(TaskStatus.CANCELED);
                            }
                        }
                        instance.setCurrentNodeIndex(targetIndex);
                        instance.incrementVersion();
                        for (ApprovalTask task : instance.getTasks()) {
                            int taskNodeIndex = nodeIndex(instance, task.getNodeId());
                            if (taskNodeIndex >= targetIndex) {
                                task.setStatus(TaskStatus.PENDING);
                            }
                        }
                        append(instance, "ROLLED_BACK", targetNodeId + ":" + reason);
                        return instance;
                    }
                });
    }

    public ApprovalInstance requireInstance(String instanceId) {
        ApprovalInstance instance = instances.get(instanceId);
        if (instance == null) {
            throw new NoSuchElementException("approval instance not found: " + instanceId);
        }
        return instance;
    }

    public List<ApprovalEvent> eventsAfter(String instanceId, long cursor) {
        requireInstance(instanceId);
        List<ApprovalEvent> source = events.get(instanceId);
        synchronized (source) {
            return source.stream().filter(e -> e.cursor() > cursor).toList();
        }
    }

    private void advance(ApprovalInstance instance) {
        for (ApprovalTask task : instance.getTasks()) {
            if (task.getNodeId().equals(instance.getCurrentNode().id())
                    && task.getStatus() == TaskStatus.PENDING) {
                task.setStatus(TaskStatus.CANCELED);
            }
        }
        int next = instance.getCurrentNodeIndex() + 1;
        if (next >= instance.getNodes().size()) {
            instance.setStatus(ApprovalStatus.APPROVED);
            append(instance, "APPROVED", instance.getId());
        } else {
            instance.setCurrentNodeIndex(next);
            append(instance, "NODE_ADVANCED", instance.getCurrentNode().id());
        }
    }

    private void cancelPendingTasks(ApprovalInstance instance) {
        instance.getTasks().stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .forEach(t -> t.setStatus(TaskStatus.CANCELED));
    }

    private void append(ApprovalInstance instance, String type, String payload) {
        ApprovalEvent event = new ApprovalEvent(
                eventCursor.incrementAndGet(), instance.getId(), type, payload);
        events.computeIfAbsent(instance.getId(),
                k -> Collections.synchronizedList(new ArrayList<>())).add(event);
    }

    private int nodeIndex(ApprovalInstance instance, String nodeId) {
        for (int i = 0; i < instance.getNodes().size(); i++) {
            if (instance.getNodes().get(i).id().equals(nodeId)) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private <T> T executeOnce(String instanceId, String idempotencyKey, String operation, java.util.function.Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        ApprovalInstance instance = requireInstance(instanceId);
        Map<String, Object> keys = idempotency.computeIfAbsent(instanceId, k -> new ConcurrentHashMap<>());
        Object existing = keys.putIfAbsent(idempotencyKey, new IdempotencyPending(operation));
        if (existing != null) {
            if (existing instanceof IdempotencyPending pending) {
                if (!pending.operation().equals(operation)) {
                    throw new IllegalArgumentException("idempotencyKey was reused for a different operation");
                }
                synchronized (instance) {
                    Object completed = keys.get(idempotencyKey);
                    if (completed instanceof IdempotencyResult result) {
                        return (T) result.value();
                    }
                    throw new IllegalStateException("idempotency operation is still pending");
                }
            }
            if (existing instanceof IdempotencyResult result) {
                if (!result.operation().equals(operation)) {
                    throw new IllegalArgumentException("idempotencyKey was reused for a different operation");
                }
                return (T) result.value();
            }
        }
        synchronized (instance) {
            T result = action.get();
            keys.put(idempotencyKey, new IdempotencyResult(operation, result));
            return result;
        }
    }

    private record IdempotencyPending(String operation) {}
    private record IdempotencyResult(String operation, Object value) {}
}
