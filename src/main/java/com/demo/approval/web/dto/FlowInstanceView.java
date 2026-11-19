package com.demo.approval.web.dto;

import com.demo.approval.domain.model.InstanceStatus;
import com.demo.approval.domain.model.SignMode;
import com.demo.approval.domain.model.VoteAction;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 实例的完整视图：接口返回值、幂等重放、事件 patch 都从这里来，保证三者字段一致。 */
public record FlowInstanceView(
        String id,
        String templateKey,
        int templateVersion,
        InstanceStatus status,
        String currentNodeId,
        String currentNodeName,
        SignMode signMode,
        int roundNo,
        long version,
        List<String> approvers,
        Map<String, VoteAction> votes,
        List<String> waitingFor,
        List<String> visitedPath,
        String initiatorId,
        Map<String, Object> form,
        Instant updatedAt) {

    /**
     * 推送用的增量：只放会变化的字段，不放 form / templateKey 这类提交后不变的数据。
     * 用 LinkedHashMap 是因为流程结束后 currentNodeId 为 null，Map.of 不允许 null 值。
     */
    public Map<String, Object> toPatch() {
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("status", status);
        patch.put("currentNodeId", currentNodeId);
        patch.put("currentNodeName", currentNodeName);
        patch.put("signMode", signMode);
        patch.put("roundNo", roundNo);
        patch.put("approvers", approvers);
        patch.put("votes", votes);
        patch.put("waitingFor", waitingFor);
        patch.put("visitedPath", visitedPath);
        patch.put("updatedAt", updatedAt);
        return patch;
    }
}
