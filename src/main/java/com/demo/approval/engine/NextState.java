package com.demo.approval.engine;

import com.demo.approval.common.VisitedPath;
import com.demo.approval.domain.entity.FlowInstance;
import com.demo.approval.domain.model.FlowEventType;
import com.demo.approval.domain.model.InstanceStatus;

/**
 * 一次状态转移之后实例应该变成的样子，交给 CAS 一次性写入。
 * 把"算下一状态"和"写库"拆开，算的部分可以纯单测。
 */
public record NextState(
        String nodeId,
        InstanceStatus status,
        int roundNo,
        String visitedPath,
        FlowEventType eventType) {

    /** 票数不够，停在当前节点，只记一票。 */
    public static NextState stay(FlowInstance instance) {
        return new NextState(instance.getCurrentNodeId(), InstanceStatus.RUNNING,
                instance.getRoundNo(), instance.getVisitedPath(), FlowEventType.VOTED);
    }

    /** 节点通过，进入下一节点。 */
    public static NextState moveTo(FlowInstance instance, String nextNodeId) {
        return new NextState(nextNodeId, InstanceStatus.RUNNING, instance.getRoundNo(),
                VisitedPath.append(instance.getVisitedPath(), nextNodeId), FlowEventType.NODE_PASSED);
    }

    /** 最后一个节点通过，流程结束。 */
    public static NextState finish(FlowInstance instance) {
        return new NextState(null, InstanceStatus.APPROVED, instance.getRoundNo(),
                instance.getVisitedPath(), FlowEventType.APPROVED);
    }

    /** 节点拒绝，流程驳回。 */
    public static NextState reject(FlowInstance instance) {
        return new NextState(null, InstanceStatus.REJECTED, instance.getRoundNo(),
                instance.getVisitedPath(), FlowEventType.REJECTED);
    }

    /** 加签：状态不变，只是版本号 +1，让其他客户端感知到审批人列表变化。 */
    public static NextState signerAdded(FlowInstance instance) {
        return new NextState(instance.getCurrentNodeId(), InstanceStatus.RUNNING,
                instance.getRoundNo(), instance.getVisitedPath(), FlowEventType.SIGNER_ADDED);
    }

    /** 回退：跳回上游节点，轮次 +1，路径截断。 */
    public static NextState rollbackTo(FlowInstance instance, String targetNodeId) {
        return new NextState(targetNodeId, InstanceStatus.RUNNING, instance.getRoundNo() + 1,
                VisitedPath.truncateTo(instance.getVisitedPath(), targetNodeId), FlowEventType.ROLLED_BACK);
    }

    public boolean nodeChanged(FlowInstance before) {
        return nodeId == null ? before.getCurrentNodeId() != null : !nodeId.equals(before.getCurrentNodeId());
    }
}
