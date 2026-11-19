package com.demo.approval.compensation;

import com.demo.approval.common.AfterCommit;
import com.demo.approval.common.JsonCodec;
import com.demo.approval.domain.entity.CompensationTask;
import com.demo.approval.repository.CompensationTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 补偿任务执行器。
 *
 * <pre>
 * runOne(taskId)
 *   ① claim：PENDING → RUNNING（CAS，防止立即执行和定时重试同时跑同一个任务）
 *   ② 在独立事务(REQUIRES_NEW)里执行补偿步骤
 *   ③ 在另一个独立事务里记录结果：DONE / PENDING(退避) / DEAD(人工处理)
 * </pre>
 *
 * 步骤②和③必须分开事务：步骤失败会把它所在的事务标记为 rollback-only，
 * 如果③和②在同一个事务里，"记录失败"这个动作也会被回滚，任务就永远卡在 RUNNING。
 */
@Component
public class CompensationExecutor {

    private static final Logger log = LoggerFactory.getLogger(CompensationExecutor.class);

    private final CompensationTaskRepository repository;
    private final Map<String, CompensationStep> stepsByName;
    private final TransactionTemplate requiresNewTx;
    private final ExecutorService executorService;
    private final JsonCodec jsonCodec;
    private final int maxAttempts;

    public CompensationExecutor(CompensationTaskRepository repository,
                                List<CompensationStep> steps,
                                @Qualifier("requiresNewTransactionTemplate") TransactionTemplate requiresNewTx,
                                @Qualifier("compensationExecutorService") ExecutorService executorService,
                                JsonCodec jsonCodec,
                                @Value("${flow.compensation.max-attempts:5}") int maxAttempts) {
        this.repository = repository;
        this.stepsByName = steps.stream().collect(Collectors.toUnmodifiableMap(CompensationStep::name, Function.identity()));
        this.requiresNewTx = requiresNewTx;
        this.executorService = executorService;
        this.jsonCodec = jsonCodec;
        this.maxAttempts = maxAttempts;
    }

    /** 业务事务提交后立刻异步执行；没跑成功的交给 CompensationRetryJob 兜底。 */
    public void runAfterCommit(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return;
        }
        AfterCommit.run(() -> executorService.submit(() -> taskIds.forEach(this::runOne)));
    }

    public boolean runOne(Long taskId) {
        Boolean claimed = requiresNewTx.execute(status -> repository.claim(taskId, Instant.now()) == 1);
        if (!Boolean.TRUE.equals(claimed)) {
            return false;
        }

        CompensationTask task = requiresNewTx.execute(status -> repository.findById(taskId).orElse(null));
        if (task == null) {
            return false;
        }

        String error = null;
        try {
            CompensationStep step = stepsByName.get(task.getStepName());
            if (step == null) {
                throw new IllegalStateException("未注册的补偿步骤: " + task.getStepName());
            }
            CompensationContext context = new CompensationContext(task.getInstanceId(), jsonCodec.readMap(task.getPayloadJson()));
            requiresNewTx.executeWithoutResult(status -> step.execute(context));
        } catch (RuntimeException e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }

        String finalError = error;
        requiresNewTx.executeWithoutResult(status -> repository.findById(taskId).ifPresent(fresh -> {
            if (finalError == null) {
                fresh.markDone();
            } else {
                fresh.markFailed(finalError, maxAttempts);
            }
        }));

        if (finalError == null) {
            log.info("[补偿] 成功 task={} step={} instance={}", taskId, task.getStepName(), task.getInstanceId());
        } else {
            log.warn("[补偿] 失败 task={} step={} instance={} attempt={} error={}",
                    taskId, task.getStepName(), task.getInstanceId(), task.getAttempts(), finalError);
        }
        return finalError == null;
    }
}
