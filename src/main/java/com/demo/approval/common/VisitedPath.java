package com.demo.approval.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 流程实例走过的节点路径，用逗号拼接存库。
 * 回退时用它判断"目标节点是否是走过的上游节点"，以及计算被跳过的节点。
 */
public final class VisitedPath {

    private static final String SEPARATOR = ",";

    private VisitedPath() {
    }

    public static List<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.stream(raw.split(SEPARATOR))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    public static String join(List<String> nodes) {
        return String.join(SEPARATOR, nodes);
    }

    public static String append(String raw, String nodeId) {
        List<String> nodes = parse(raw);
        nodes.add(nodeId);
        return join(nodes);
    }

    /** 截断到 targetNodeId（含），返回新路径。 */
    public static String truncateTo(String raw, String targetNodeId) {
        List<String> nodes = parse(raw);
        int index = nodes.lastIndexOf(targetNodeId);
        if (index < 0) {
            throw new IllegalArgumentException("路径中不存在节点: " + targetNodeId);
        }
        return join(nodes.subList(0, index + 1));
    }
}
