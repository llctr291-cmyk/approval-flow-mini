package com.demo.approval.sync;

import com.demo.approval.common.JsonCodec;
import com.demo.approval.web.dto.FlowEventView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 推送中心。
 *
 * <p>连接地址：{@code /ws/flow?instanceId=xxx}（不带 instanceId 表示订阅全部，例如待办列表页）。</p>
 *
 * <p>WebSocketSession 本身不是线程安全的，多个审批事务同时 afterCommit 推送会并发写同一个连接，
 * 所以用 {@link ConcurrentWebSocketSessionDecorator} 包一层：内部排队发送，
 * 超过发送时间或缓冲区上限就断开慢客户端（它重连后会自己补拉，不会丢数据）。</p>
 */
@Component
public class FlowWebSocketHub extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(FlowWebSocketHub.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

    private record Subscriber(WebSocketSession session, String instanceFilter) {
        boolean accepts(String instanceId) {
            return instanceFilter == null || instanceFilter.equals(instanceId);
        }
    }

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final JsonCodec jsonCodec;

    public FlowWebSocketHub(JsonCodec jsonCodec) {
        this.jsonCodec = jsonCodec;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String instanceFilter = queryParam(session.getUri(), "instanceId");
        WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        subscribers.put(session.getId(), new Subscriber(safeSession, instanceFilter));
        log.info("[WS] 连接建立 session={} filter={} online={}", session.getId(), instanceFilter, subscribers.size());
        send(safeSession, Map.of("kind", "hello", "sessionId", session.getId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端心跳：收到 ping 回 pong，用于检测半开连接
        if ("ping".equals(message.getPayload())) {
            Subscriber subscriber = subscribers.get(session.getId());
            if (subscriber != null) {
                send(subscriber.session(), Map.of("kind", "pong"));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscribers.remove(session.getId());
        log.info("[WS] 连接关闭 session={} status={} online={}", session.getId(), status, subscribers.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[WS] 传输错误 session={} error={}", session.getId(), exception.getMessage());
        subscribers.remove(session.getId());
    }

    /** 只推增量 patch；推送失败不抛异常 —— 推送是尽力而为，可靠性由事件表 + 补拉保证。 */
    public void broadcast(FlowEventView event) {
        Map<String, Object> envelope = Map.of("kind", "event", "data", event);
        subscribers.values().stream()
                .filter(subscriber -> subscriber.accepts(event.instanceId()))
                .forEach(subscriber -> send(subscriber.session(), envelope));
    }

    public int onlineCount() {
        return subscribers.size();
    }

    private void send(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            subscribers.remove(session.getId());
            return;
        }
        try {
            session.sendMessage(new TextMessage(jsonCodec.write(payload)));
        } catch (IOException | IllegalStateException e) {
            log.warn("[WS] 推送失败，断开连接 session={} error={}", session.getId(), e.getMessage());
            subscribers.remove(session.getId());
            closeQuietly(session);
        }
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException ignored) {
            // 连接已经坏了，关闭失败也无所谓
        }
    }

    private static String queryParam(URI uri, String name) {
        if (uri == null) {
            return null;
        }
        String value = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst(name);
        return value == null || value.isBlank() ? null : value;
    }
}
