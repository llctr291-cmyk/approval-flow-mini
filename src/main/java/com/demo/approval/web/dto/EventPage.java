package com.demo.approval.web.dto;

import java.util.List;

/**
 * 补拉分页结果。
 *
 * @param nextCursor 下一页的 afterSeq；没有新事件时等于请求的 afterSeq
 */
public record EventPage(List<FlowEventView> events, boolean hasMore, long nextCursor) {
}
