package com.demo.approval.service;

import com.demo.approval.common.BusinessException;
import com.demo.approval.common.ErrorCode;
import com.demo.approval.domain.entity.FlowInstance;
import com.demo.approval.engine.NextState;
import com.demo.approval.repository.FlowInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 所有状态变更的唯一出口：审批、加签、回退都通过这里做 CAS。
 * 集中在一处，保证"每次状态变化 version 一定 +1"这个不变量不会被某条分支漏掉。
 */
@Component
public class FlowStateWriter {

    private static final Logger log = LoggerFactory.getLogger(FlowStateWriter.class);

    private final FlowInstanceRepository instanceRepository;

    public FlowStateWriter(FlowInstanceRepository instanceRepository) {
        this.instanceRepository = instanceRepository;
    }

    /**
     * @return 写入后的新版本号
     * @throws BusinessException VERSION_CONFLICT：并发下没抢到，调用方事务会整体回滚
     */
    public long apply(String instanceId, long expectedVersion, NextState next) {
        int updated = instanceRepository.compareAndSet(
                instanceId,
                expectedVersion,
                next.nodeId(),
                next.status(),
                next.roundNo(),
                next.visitedPath(),
                Instant.now());
        if (updated == 0) {
            log.warn("[CAS] 冲突 instance={} expectedVersion={} event={}", instanceId, expectedVersion, next.eventType());
            throw BusinessException.concurrentModified();
        }
        long newVersion = expectedVersion + 1;
        log.info("[CAS] 成功 instance={} version {}->{} event={} node={} status={}",
                instanceId, expectedVersion, newVersion, next.eventType(), next.nodeId(), next.status());
        return newVersion;
    }

    public static void ensureRunning(FlowInstance instance) {
        if (instance.getStatus().isFinished()) {
            throw new BusinessException(ErrorCode.INVALID_STATE, "流程已结束，当前状态: " + instance.getStatus());
        }
    }

    /** 快速失败：版本明显过期时不用走到 CAS 那一步再失败，省掉无意义的写操作。 */
    public static void ensureVersion(FlowInstance instance, long expectedVersion) {
        if (instance.getVersion() != expectedVersion) {
            throw BusinessException.versionConflict(expectedVersion, instance.getVersion());
        }
    }
}
