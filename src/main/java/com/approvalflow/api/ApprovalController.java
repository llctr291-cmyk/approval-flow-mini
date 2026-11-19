package com.approvalflow.api;

import com.approvalflow.domain.*;
import com.approvalflow.service.ApprovalEngine;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {
    private final ApprovalEngine engine;

    public ApprovalController(ApprovalEngine engine) {
        this.engine = engine;
    }

    @PostMapping
    public ApprovalInstance create(@RequestBody Map<String, Object> body) {
        String applicant = String.valueOf(body.get("applicant"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodeMaps = (List<Map<String, Object>>) body.get("nodes");
        List<ApprovalNode> nodes = nodeMaps.stream().map(node -> new ApprovalNode(
                String.valueOf(node.get("id")),
                CounterMode.valueOf(String.valueOf(node.getOrDefault("mode", "ALL"))),
                ((List<?>) node.getOrDefault("assignees", List.of())).stream()
                        .map(String::valueOf).toList()
        )).toList();
        return engine.create(applicant, nodes);
    }

    @PostMapping("/{instanceId}/tasks/{taskId}/actions")
    public ApprovalInstance act(@PathVariable String instanceId,
                                @PathVariable String taskId,
                                @RequestBody Map<String, Object> body) {
        return engine.act(instanceId, taskId,
                Decision.valueOf(String.valueOf(body.get("decision"))),
                String.valueOf(body.getOrDefault("operator", "unknown")),
                String.valueOf(body.get("idempotencyKey")));
    }

    @PostMapping("/{instanceId}/nodes/{nodeId}/add-sign")
    public ApprovalInstance addSign(@PathVariable String instanceId,
                                    @PathVariable String nodeId,
                                    @RequestBody Map<String, Object> body) {
        return engine.addSign(instanceId, nodeId, String.valueOf(body.get("assignee")),
                String.valueOf(body.get("idempotencyKey")));
    }

    @PostMapping("/{instanceId}/rollback")
    public ApprovalInstance rollback(@PathVariable String instanceId,
                                     @RequestBody Map<String, Object> body) {
        return engine.rollback(instanceId, String.valueOf(body.get("targetNodeId")),
                String.valueOf(body.getOrDefault("reason", "")),
                ((Number) body.get("expectedVersion")).longValue(),
                String.valueOf(body.get("idempotencyKey")));
    }

    @GetMapping("/{instanceId}/events")
    public List<ApprovalEvent> events(@PathVariable String instanceId,
                                      @RequestParam(defaultValue = "0") long cursor) {
        return engine.eventsAfter(instanceId, cursor);
    }

    @GetMapping("/{instanceId}")
    public ApprovalInstance get(@PathVariable String instanceId) {
        return engine.requireInstance(instanceId);
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(NoSuchElementException exception) {
        return Map.of("error", exception.getMessage());
    }
}
