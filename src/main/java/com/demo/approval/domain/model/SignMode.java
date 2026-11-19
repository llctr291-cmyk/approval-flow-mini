package com.demo.approval.domain.model;

/** 多人节点的签署模式。 */
public enum SignMode {
    /** 会签：所有人都同意才通过，任意一人拒绝即拒绝。 */
    ALL,
    /** 或签：任意一人同意即通过，所有人都拒绝才拒绝。 */
    ANY
}
