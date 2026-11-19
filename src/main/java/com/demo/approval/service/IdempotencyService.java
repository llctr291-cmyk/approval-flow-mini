package com.demo.approval.service;

import com.demo.approval.common.BusinessException;
import com.demo.approval.common.ErrorCode;
import com.demo.approval.common.JsonCodec;
import com.demo.approval.domain.entity.IdempotencyRecord;
import com.demo.approval.repository.IdempotencyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 幂等保护。
 *
 * <p>流程：先查有没有处理过 → 处理业务 → 在<b>同一个事务</b>里把响应存下来。
 * 两个相同 key 的请求同时进来时，后提交的那个会撞主键唯一约束，整个事务回滚，
 * 由全局异常处理器转成 409 DUPLICATE_REQUEST，客户端稍后重试就能拿到第一次的结果。</p>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final int MAX_KEY_LENGTH = 128;

    private final IdempotencyRecordRepository repository;
    private final JsonCodec jsonCodec;
    private final Duration ttl;

    public IdempotencyService(IdempotencyRecordRepository repository,
                              JsonCodec jsonCodec,
                              @Value("${flow.idempotency.ttl-hours:24}") long ttlHours) {
        this.repository = repository;
        this.jsonCodec = jsonCodec;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public <T> Optional<T> findReplay(String operatorId, String idempotencyKey, Class<T> responseType) {
        if (isBlank(idempotencyKey)) {
            return Optional.empty();
        }
        return repository.findById(recordKey(operatorId, idempotencyKey))
                .filter(record -> record.getCreatedAt().isAfter(Instant.now().minus(ttl)))
                .map(record -> {
                    log.info("[幂等] 命中重放 operator={} key={}", operatorId, idempotencyKey);
                    return jsonCodec.read(record.getResponseJson(), responseType);
                });
    }

    /** 必须在业务事务内调用：业务回滚时幂等记录一起回滚，不会出现"记录存了但业务没做"。 */
    public void remember(String operatorId, String idempotencyKey, Object response) {
        if (isBlank(idempotencyKey)) {
            return;
        }
        repository.saveAndFlush(new IdempotencyRecord(
                recordKey(operatorId, idempotencyKey), operatorId, jsonCodec.write(response)));
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeExpired() {
        int deleted = repository.deleteExpired(Instant.now().minus(ttl));
        if (deleted > 0) {
            log.info("[幂等] 清理过期记录 {} 条", deleted);
        }
    }

    private String recordKey(String operatorId, String idempotencyKey) {
        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Idempotency-Key 长度不能超过 " + MAX_KEY_LENGTH);
        }
        // 按用户隔离：不同用户恰好用了同一个 key 也不会互相串结果
        return operatorId + ":" + idempotencyKey;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
