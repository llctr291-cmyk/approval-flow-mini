package com.demo.approval.web.dto;

import com.demo.approval.domain.model.FlowEventType;

import java.time.Instant;
import java.util.Map;

/**
 * 推送 / 补拉给客户端的事件。
 *
 * @param seq     全局游标，客户端补拉时带 afterSeq
 * @param version 实例版本号，客户端只接受比本地更大的
 * @param patch   增量字段
 */
public record FlowEventView(
        long seq,
        String instanceId,
        long version,
        FlowEventType type,
        String operatorId,
        Map<String, Object> patch,
        Instant createdAt) {
}
