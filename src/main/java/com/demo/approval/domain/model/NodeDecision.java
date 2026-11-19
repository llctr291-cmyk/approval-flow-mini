package com.demo.approval.domain.model;

/** 一个节点在当前投票情况下的收敛结果。 */
public enum NodeDecision {
    /** 节点通过，流转到下一节点或结束。 */
    PASSED,
    /** 节点拒绝，整个流程驳回。 */
    REJECTED,
    /** 票数还不够，继续等其他人。 */
    PENDING
}
