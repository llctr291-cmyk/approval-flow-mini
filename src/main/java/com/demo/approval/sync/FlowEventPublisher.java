package com.demo.approval.sync;

import com.demo.approval.common.JsonCodec;
import com.demo.approval.domain.entity.FlowEvent;
import com.demo.approval.domain.model.FlowEventType;
import com.demo.approval.repository.FlowEventRepository;
import com.demo.approval.web.dto.FlowEventView;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * 事件发布：先落库（Outbox），事务提交成功之后再推 WebSocket。
 *
 * <p>为什么不在事务里直接推？如果推送之后事务回滚了（比如 CAS 失败），
 * 客户端就看到了一个"从未发生过"的状态。afterCommit 保证"推出去的一定是已经提交的"。</p>
 *
 * <p>推送丢了怎么办？没关系，事件已经在库里，客户端断线重连 / 定期对账时按 seq 补拉。
 * 所以 WebSocket 只是"快速通道"，事件表才是"真相来源"。</p>
 */
@Component
public class FlowEventPublisher {

    private final FlowEventRepository eventRepository;
    private final FlowWebSocketHub webSocketHub;
    private final JsonCodec jsonCodec;

    public FlowEventPublisher(FlowEventRepository eventRepository, FlowWebSocketHub webSocketHub, JsonCodec jsonCodec) {
        this.eventRepository = eventRepository;
        this.webSocketHub = webSocketHub;
        this.jsonCodec = jsonCodec;
    }

    public FlowEventView publish(String instanceId, long instanceVersion, FlowEventType type,
                                 String operatorId, Map<String, Object> patch) {
        FlowEvent saved = eventRepository.save(
                new FlowEvent(instanceId, instanceVersion, type, operatorId, jsonCodec.write(patch)));
        FlowEventView view = new FlowEventView(saved.getSeq(), instanceId, instanceVersion, type,
                operatorId, patch, saved.getCreatedAt());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    webSocketHub.broadcast(view);
                }
            });
        } else {
            webSocketHub.broadcast(view);
        }
        return view;
    }

    public FlowEventView toView(FlowEvent event) {
        return new FlowEventView(event.getSeq(), event.getInstanceId(), event.getInstanceVersion(),
                event.getType(), event.getOperatorId(), jsonCodec.readMap(event.getPatchJson()), event.getCreatedAt());
    }
}
