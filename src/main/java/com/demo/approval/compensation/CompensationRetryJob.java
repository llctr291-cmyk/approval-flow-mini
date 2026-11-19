package com.demo.approval.compensation;

import com.demo.approval.domain.entity.CompensationTask;
import com.demo.approval.repository.CompensationTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 兜底重试：扫描到期的 PENDING 任务重新执行；RUNNING 超过租约（执行器崩溃）的放回 PENDING。
 * 多实例部署时 claim 的 CAS 保证同一个任务只会被一个节点执行。
 */
@Component
public class CompensationRetryJob {

    private static final Logger log = LoggerFactory.getLogger(CompensationRetryJob.class);
    private static final Duration RUNNING_LEASE = Duration.ofMinutes(1);

    private final CompensationTaskRepository repository;
    private final CompensationExecutor executor;
    private final TransactionTemplate requiresNewTx;

    public CompensationRetryJob(CompensationTaskRepository repository,
                                CompensationExecutor executor,
                                @Qualifier("requiresNewTransactionTemplate") TransactionTemplate requiresNewTx) {
        this.repository = repository;
        this.executor = executor;
        this.requiresNewTx = requiresNewTx;
    }

    @Scheduled(fixedDelayString = "${flow.compensation.retry-interval-ms:5000}")
    public void retryDueTasks() {
        Instant now = Instant.now();
        Integer released = requiresNewTx.execute(status -> repository.releaseExpiredLeases(now.minus(RUNNING_LEASE), now));
        if (released != null && released > 0) {
            log.warn("[补偿] 回收超时租约 {} 个", released);
        }

        List<CompensationTask> due = repository.findTop50ByStatusAndNextRunAtLessThanEqualOrderByIdAsc(
                CompensationTask.Status.PENDING, now);
        if (due.isEmpty()) {
            return;
        }
        long succeeded = due.stream().filter(task -> executor.runOne(task.getId())).count();
        log.info("[补偿] 定时重试 total={} succeeded={}", due.size(), succeeded);
    }
}
