package com.demo.approval.domain.model;

/** 推送给客户端的事件类型。每个事件都对应实例的一次 version 递增。 */
public enum FlowEventType {
    SUBMITTED,
    /** 有人投票，但节点还没收敛。 */
    VOTED,
    /** 节点通过，进入下一节点。 */
    NODE_PASSED,
    APPROVED,
    REJECTED,
    SIGNER_ADDED,
    ROLLED_BACK
}
