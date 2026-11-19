package com.demo.approval.service;

import com.demo.approval.common.BusinessException;
import com.demo.approval.common.ErrorCode;
import com.demo.approval.domain.entity.BudgetLock;
import com.demo.approval.repository.BudgetLockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * 预算占用（模拟外部资源）。用来演示"状态回退时，已经产生的副作用也要撤销"：
 * 提交锁定 → 通过扣减；驳回或回退到起点 → 释放（补偿动作）。
 */
@Service
public class BudgetService {

    private static final Logger log = LoggerFactory.getLogger(BudgetService.class);

    private final BudgetLockRepository repository;

    public BudgetService(BudgetLockRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void lockIfPresent(String instanceId, Map<String, Object> form) {
        parseAmount(form).ifPresent(amount -> {
            repository.save(new BudgetLock(instanceId, amount));
            log.info("[预算] 锁定 instance={} amount={}", instanceId, amount);
        });
    }

    @Transactional
    public void consume(String instanceId) {
        repository.findById(instanceId).ifPresent(lock -> {
            if (lock.consume()) {
                log.info("[预算] 扣减 instance={} amount={}", instanceId, lock.getAmount());
            }
        });
    }

    /** 幂等释放：已经释放或已扣减时什么都不做。 */
    @Transactional
    public boolean release(String instanceId) {
        return repository.findById(instanceId)
                .map(lock -> {
                    boolean changed = lock.release();
                    if (changed) {
                        log.info("[预算] 释放 instance={} amount={}", instanceId, lock.getAmount());
                    }
                    return changed;
                })
                .orElse(false);
    }

    /** 回退到起点后重新审批通过起点节点时，再次锁定。 */
    @Transactional
    public void relock(String instanceId) {
        repository.findById(instanceId).ifPresent(lock -> {
            if (lock.relock()) {
                log.info("[预算] 重新锁定 instance={} amount={}", instanceId, lock.getAmount());
            }
        });
    }

    private Optional<BigDecimal> parseAmount(Map<String, Object> form) {
        Object raw = form.get("amount");
        if (raw == null) {
            return Optional.empty();
        }
        try {
            BigDecimal amount = new BigDecimal(raw.toString());
            if (amount.signum() < 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "金额不能为负数");
            }
            return Optional.of(amount);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "金额格式不正确: " + raw);
        }
    }
}
