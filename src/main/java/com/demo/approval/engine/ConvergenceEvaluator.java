package com.demo.approval.engine;

import com.demo.approval.common.BusinessException;
import com.demo.approval.common.ErrorCode;
import com.demo.approval.domain.model.NodeDecision;
import com.demo.approval.domain.model.SignMode;
import com.demo.approval.domain.model.VoteAction;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * 会签 / 或签收敛判断，纯函数，不碰数据库，方便单测穷举。
 *
 * <table>
 *   <tr><th>模式</th><th>通过</th><th>拒绝</th></tr>
 *   <tr><td>ALL 会签</td><td>所有人同意</td><td>任意一人拒绝</td></tr>
 *   <tr><td>ANY 或签</td><td>任意一人同意</td><td>所有人拒绝</td></tr>
 * </table>
 *
 * 只统计 approvers 里的票：被移出审批人列表的人投过的票不算数。
 */
@Component
public class ConvergenceEvaluator {

    public NodeDecision evaluate(SignMode mode, Collection<String> approvers, Map<String, VoteAction> votes) {
        if (approvers == null || approvers.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "节点没有审批人，无法收敛");
        }
        long agreeCount = approvers.stream().filter(id -> votes.get(id) == VoteAction.AGREE).count();
        long rejectCount = approvers.stream().filter(id -> votes.get(id) == VoteAction.REJECT).count();
        int total = approvers.size();

        return switch (mode) {
            case ANY -> {
                if (agreeCount >= 1) {
                    yield NodeDecision.PASSED;
                }
                yield rejectCount == total ? NodeDecision.REJECTED : NodeDecision.PENDING;
            }
            case ALL -> {
                if (rejectCount >= 1) {
                    yield NodeDecision.REJECTED;
                }
                yield agreeCount == total ? NodeDecision.PASSED : NodeDecision.PENDING;
            }
        };
    }

    /** 还没投票的审批人，用于界面展示"等谁"。 */
    public java.util.List<String> waitingFor(Collection<String> approvers, Map<String, VoteAction> votes) {
        return approvers.stream().filter(id -> !votes.containsKey(id)).toList();
    }
}
