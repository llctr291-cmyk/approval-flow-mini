package com.demo.approval.compensation;

import java.util.List;
import java.util.Map;

/** 补偿步骤的执行上下文。payload 来自 compensation_task.payload_json。 */
public record CompensationContext(String instanceId, Map<String, Object> payload) {

    public int intValue(String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            return Integer.parseInt(text);
        }
        throw new IllegalArgumentException("补偿参数缺失或类型错误: " + key);
    }

    public String stringValue(String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    public List<String> stringList(String key) {
        Object value = payload.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
