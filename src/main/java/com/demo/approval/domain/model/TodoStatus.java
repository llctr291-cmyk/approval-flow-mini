package com.demo.approval.domain.model;

public enum TodoStatus {
    PENDING,
    DONE,
    /** 被收敛（或签他人已通过）或回退补偿撤销。 */
    CANCELLED
}
