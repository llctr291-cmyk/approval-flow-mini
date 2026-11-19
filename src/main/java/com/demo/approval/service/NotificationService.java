package com.demo.approval.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 通知服务（模拟外部 IM / 邮件）。
 *
 * <p>{@code flow.notify.failure-rate} 可以注入随机故障（0~1），
 * 用来验证补偿任务的重试与 DEAD 队列是否真的生效 —— 这类混沌测试正适合交给 Devin 批量跑。</p>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final double failureRate;

    public NotificationService(@Value("${flow.notify.failure-rate:0}") double failureRate) {
        this.failureRate = failureRate;
    }

    public void notifyUsers(Collection<String> userIds, String message) {
        if (userIds.isEmpty()) {
            return;
        }
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new IllegalStateException("模拟通知服务故障");
        }
        log.info("[通知] to={} message={}", userIds, message);
    }
}
