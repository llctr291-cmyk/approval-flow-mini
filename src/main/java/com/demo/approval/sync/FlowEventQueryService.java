package com.demo.approval.sync;

import com.demo.approval.domain.entity.FlowEvent;
import com.demo.approval.repository.FlowEventRepository;
import com.demo.approval.web.dto.EventPage;
import com.demo.approval.web.dto.FlowEventView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 断线补偿：客户端带着 lastSeq 来拿漏掉的事件。
 *
 * <p><b>读延迟窗口（read-lag）</b>：seq 是插入时分配的，但事务提交有先后。
 * 可能出现 seq=101 的事务还没提交，seq=102 已经提交并被客户端拿走，游标推进到 102，
 * 之后 101 提交了却再也不会被拉到。只返回 N 毫秒之前写入的事件，
 * 给慢事务留出提交时间，大幅降低这种"跳号"概率。</p>
 */
@Service
@Transactional(readOnly = true)
public class FlowEventQueryService {

    private final FlowEventRepository eventRepository;
    private final FlowEventPublisher eventPublisher;
    private final Duration readLag;
    private final int maxPageSize;

    public FlowEventQueryService(FlowEventRepository eventRepository,
                                 FlowEventPublisher eventPublisher,
                                 @Value("${flow.events.read-lag-ms:300}") long readLagMs,
                                 @Value("${flow.events.max-page-size:500}") int maxPageSize) {
        this.eventRepository = eventRepository;
        this.eventPublisher = eventPublisher;
        this.readLag = Duration.ofMillis(readLagMs);
        this.maxPageSize = maxPageSize;
    }

    public EventPage eventsAfter(long afterSeq, String instanceId, int limit) {
        int pageSize = Math.max(1, Math.min(limit, maxPageSize));
        Instant visibleBefore = Instant.now().minus(readLag);
        String filter = (instanceId == null || instanceId.isBlank()) ? null : instanceId;

        // 多查一条用来判断 hasMore，省一次 count 查询
        List<FlowEvent> rows = eventRepository.findPage(Math.max(0, afterSeq), filter, visibleBefore,
                PageRequest.of(0, pageSize + 1));
        boolean hasMore = rows.size() > pageSize;
        List<FlowEventView> events = rows.stream()
                .limit(pageSize)
                .map(eventPublisher::toView)
                .toList();
        long nextCursor = events.isEmpty() ? afterSeq : events.getLast().seq();
        return new EventPage(events, hasMore, nextCursor);
    }

    public List<FlowEventView> timeline(String instanceId) {
        return eventRepository.findByInstanceIdOrderBySeqAsc(instanceId).stream()
                .map(eventPublisher::toView)
                .toList();
    }
}
