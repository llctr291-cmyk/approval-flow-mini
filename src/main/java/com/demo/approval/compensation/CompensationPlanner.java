package com.demo.approval.compensation;

import com.demo.approval.common.JsonCodec;
import com.demo.approval.domain.entity.CompensationTask;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 根据回退场景生成补偿任务清单（只生成，不执行）。 */
@Component
public class CompensationPlanner {

    private final JsonCodec jsonCodec;

    public CompensationPlanner(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    public List<CompensationTask> planForRollback(String instanceId,
                                                  int oldRound,
                                                  List<String> skippedNodes,
                                                  boolean backToStart,
                                                  List<String> currentApprovers,
                                                  String reason) {
        List<CompensationTask> tasks = new ArrayList<>();

        tasks.add(task(instanceId, CancelTodosStep.NAME, Map.of("roundNo", oldRound)));

        if (backToStart) {
            tasks.add(task(instanceId, ReleaseBudgetStep.NAME, Map.of()));
        }

        if (!currentApprovers.isEmpty()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userIds", currentApprovers);
            payload.put("skippedNodes", skippedNodes);
            payload.put("message", "单据已被退回" + (reason == null || reason.isBlank() ? "" : "（" + reason + "）") + "，无需处理");
            tasks.add(task(instanceId, NotifyApproversStep.NAME, payload));
        }
        return tasks;
    }

    private CompensationTask task(String instanceId, String stepName, Map<String, Object> payload) {
        return new CompensationTask(instanceId, stepName, jsonCodec.write(payload));
    }
}
