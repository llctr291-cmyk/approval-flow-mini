package com.demo.approval.web.dto;

import com.demo.approval.domain.entity.TodoTask;
import com.demo.approval.domain.model.TodoStatus;

import java.time.Instant;

public record TodoView(Long id, String instanceId, String nodeId, int roundNo, String assigneeId,
                       TodoStatus status, Instant createdAt) {

    public static TodoView from(TodoTask task) {
        return new TodoView(task.getId(), task.getInstanceId(), task.getNodeId(), task.getRoundNo(),
                task.getAssigneeId(), task.getStatus(), task.getCreatedAt());
    }
}
